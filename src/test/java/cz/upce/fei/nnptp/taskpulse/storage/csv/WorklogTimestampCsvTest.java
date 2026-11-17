package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.storage.IWorkspaceUnitOfWork;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorklogTimestampCsvTest {
    @TempDir
    Path workspace;
    
    @Test
    void loadsLeapDayTimestampWithNanosecondPrecision() throws IOException {
        CsvStorageManager storage = new CsvStorageManager(workspace);
        UUID taskId = writeWorklog(storage, "2024-02-29T23:59:59.123456789");
        Task task;
        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            task = unitOfWork.tasks().findById(taskId).orElseThrow();
        }
        assertThat(task.getWorklogs()).containsExactly(
                new Worklog(LocalDateTime.of(2024, 2, 29, 23, 59, 59, 123_456_789), 1, "Student"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
            "2023-02-29T12:00:00",
            "2024-02-29T24:00:00",
            "2024-02-29T23:59:60",
            "2024-02-29T23:59:59Z"
    })
    void rejectsInvalidLocalTimestampsWithCsvContext(String timestamp) throws IOException {
        CsvStorageManager storage = new CsvStorageManager(workspace);
        writeWorklog(storage, timestamp);
        assertThatThrownBy(storage::openUnitOfWork)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("worklogs.csv at line 2")
                .hasMessageContaining("column 'timestamp'")
                .hasMessageContaining(timestamp);
    }
    
    private UUID writeWorklog(CsvStorageManager storage, String timestamp) throws IOException {
        UUID taskId;
        try (IWorkspaceUnitOfWork unitOfWork = storage.openUnitOfWork()) {
            Project project = unitOfWork.projects().save(new Project("Calendar"));
            Task task = unitOfWork.tasks().save(new Task(project.getId(), "Timed task"));
            taskId = task.getId();
            unitOfWork.commit();
        }
        Files.writeString(workspace.resolve("worklogs.csv"),
                "taskId,timestamp,durationMinutes,author,description\n"
                        + taskId + "," + timestamp + ",1,Student,\n");
        return taskId;
    }
}
