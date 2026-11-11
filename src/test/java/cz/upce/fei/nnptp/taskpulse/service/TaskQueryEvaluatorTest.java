package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskBuilder;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskQueryEvaluatorTest {
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @ParameterizedTest(name = "{0}")
    @MethodSource("queries")
    void preservesFiltersAndServiceOrdering(String scenario, TaskQuery query, List<String> expected) {
        List<Task> tasks = List.of(
                new TaskBuilder(PROJECT, "Alpha backend").description("API issue").priority(Priority.HIGH)
                        .dueDate(TODAY.minusDays(1)).labels(List.of("backend")).estimatedMinutes(30)
                        .worklogs(List.of(worklog(45))).build(),
                new TaskBuilder(PROJECT, "Beta").description("Backend maintenance").priority(Priority.LOW)
                        .state(TaskState.IN_PROGRESS).dueDate(TODAY.plusDays(1)).labels(List.of("ops"))
                        .estimatedMinutes(30).worklogs(List.of(worklog(45))).build(),
                new TaskBuilder(PROJECT, "Gamma").description("Frontend").priority(Priority.CRITICAL)
                        .state(TaskState.DONE).dueDate(TODAY.minusDays(2)).labels(List.of("backend"))
                        .worklogs(List.of(worklog(60))).build(),
                new TaskBuilder(PROJECT, "Delta").estimatedMinutes(0)
                        .worklogs(List.of(worklog(1))).build(),
                new TaskBuilder(OTHER_PROJECT, "Epsilon").description("Other project backend")
                        .priority(Priority.HIGH).state(TaskState.BLOCKED).dueDate(TODAY)
                        .labels(List.of("backend")).estimatedMinutes(10).worklogs(List.of(worklog(10))).build()
        );

        TaskSpecification specification = TaskQueryEvaluator.toSpecification(query, TODAY);
        assertThat(tasks.stream().filter(specification::isSatisfiedBy).map(Task::getTitle).toList())
                .containsExactlyInAnyOrderElementsOf(expected);

        InMemoryProjectRepository projects = new InMemoryProjectRepository();
        projects.save(new Project(PROJECT, "Main", ""));
        projects.save(new Project(OTHER_PROJECT, "Other", ""));
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        tasks.forEach(repository::save);
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        TaskService service = new TaskService(repository, projects, clock);

        List<String> sortedExpected = expected.stream().sorted(Comparator.naturalOrder()).toList();
        assertThat(service.findTasks(query)).extracting(Task::getTitle)
                .containsExactlyElementsOf(sortedExpected);
        assertThat(repository.findAll()).containsExactlyInAnyOrderElementsOf(tasks);
    }

    private static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of("no filters", TaskQuery.builder().build(),
                        List.of("Alpha backend", "Beta", "Gamma", "Delta", "Epsilon")),
                Arguments.of("project", TaskQuery.builder().projectId(PROJECT).build(),
                        List.of("Alpha backend", "Beta", "Gamma", "Delta")),
                Arguments.of("state", TaskQuery.builder().state(TaskState.TODO).build(),
                        List.of("Alpha backend", "Delta")),
                Arguments.of("priority", TaskQuery.builder().priority(Priority.HIGH).build(),
                        List.of("Alpha backend", "Epsilon")),
                Arguments.of("normalized label", TaskQuery.builder().label(" BACKEND ").build(),
                        List.of("Alpha backend", "Gamma", "Epsilon")),
                Arguments.of("text in title or description", TaskQuery.builder().textSearch(" BACKEND ").build(),
                        List.of("Alpha backend", "Beta", "Epsilon")),
                Arguments.of("inclusive dates", TaskQuery.builder()
                                .dueDateRange(TODAY.minusDays(1), TODAY.plusDays(1)).build(),
                        List.of("Alpha backend", "Beta", "Epsilon")),
                Arguments.of("lower date bound", TaskQuery.builder().dueDateRange(TODAY, null).build(),
                        List.of("Beta", "Epsilon")),
                Arguments.of("upper date bound", TaskQuery.builder()
                                .dueDateRange(null, TODAY.minusDays(1)).build(),
                        List.of("Alpha backend", "Gamma")),
                Arguments.of("overdue excludes done and due today", TaskQuery.builder().overdue(true).build(),
                        List.of("Alpha backend")),
                Arguments.of("not overdue includes missing dates", TaskQuery.builder().overdue(false).build(),
                        List.of("Beta", "Gamma", "Delta", "Epsilon")),
                Arguments.of("positive variance includes zero estimate", TaskQuery.builder()
                                .varianceThresholdMinutes(0).build(),
                        List.of("Alpha backend", "Beta", "Delta")),
                Arguments.of("variance is strictly above threshold", TaskQuery.builder()
                                .varianceThresholdMinutes(15).build(), List.of()),
                Arguments.of("all filters combined", TaskQuery.builder().projectId(PROJECT).state(TaskState.TODO)
                                .priority(Priority.HIGH).label("backend").textSearch("api")
                                .dueDateRange(TODAY.minusDays(1), TODAY.minusDays(1)).overdue(true)
                                .varianceThresholdMinutes(14).build(), List.of("Alpha backend")),
                Arguments.of("no matches", TaskQuery.builder().state(TaskState.DONE)
                        .priority(Priority.LOW).build(), List.of())
        );
    }

    @Test
    void composesRealQueryFiltersWithOrAndNot() {
        Task urgent = new TaskBuilder(PROJECT, "Urgent task").priority(Priority.HIGH).build();
        Task backend = new TaskBuilder(PROJECT, "Backend task").priority(Priority.LOW)
                .labels(List.of("backend")).build();
        Task completed = new TaskBuilder(PROJECT, "Completed task").priority(Priority.HIGH)
                .state(TaskState.DONE).labels(List.of("backend"))
                .worklogs(List.of(worklog(5))).build();
        Task ordinary = new TaskBuilder(PROJECT, "Ordinary task").priority(Priority.LOW).build();

        TaskSpecification highPriority = TaskQueryEvaluator.toSpecification(
                TaskQuery.builder().priority(Priority.HIGH).build(), TODAY);
        TaskSpecification backendLabel = TaskQueryEvaluator.toSpecification(
                TaskQuery.builder().label("backend").build(), TODAY);
        TaskSpecification done = TaskQueryEvaluator.toSpecification(
                TaskQuery.builder().state(TaskState.DONE).build(), TODAY);

        TaskSpecification selected = highPriority.or(backendLabel).and(done.not());
        List<Task> result = Stream.of(urgent, backend, completed, ordinary)
                .filter(selected::isSatisfiedBy)
                .toList();

        assertThat(result).containsExactly(urgent, backend);
    }

    private static Worklog worklog(long minutes) {
        return new Worklog(TODAY.atTime(9, 0), minutes, "Student", "Test work");
    }

    @Test
    void rejectsMissingQueryOrReferenceDate() {
        assertThatThrownBy(() -> TaskQueryEvaluator.toSpecification(null, TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskQueryEvaluator.toSpecification(TaskQuery.builder().build(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
