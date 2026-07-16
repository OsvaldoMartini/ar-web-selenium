package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.SplitDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTestMessageMetadataServiceTest {

    @Test
    void enrichesSplitWithMatchingBotJobMetadata() {
        RecordingData data = new RecordingData();
        data.jobs.add(job(41, "Other", "Web App"));
        data.jobs.add(job(42, "Mobile Login", "Android"));
        ScannerTestMessageMetadataService service = new ScannerTestMessageMetadataService(data);
        SplitDTO split = new SplitDTO();
        split.setBotJobId(42);

        service.enrich(split);

        assertEquals("Mobile Login", split.getBotJobName());
        assertEquals("Android", split.getProjectType());
    }

    @Test
    void leavesSplitUnchangedWhenNoBotJobMatches() {
        RecordingData data = new RecordingData();
        data.jobs.add(job(41, "Other", "Web App"));
        ScannerTestMessageMetadataService service = new ScannerTestMessageMetadataService(data);
        SplitDTO split = new SplitDTO();
        split.setBotJobId(42);
        split.setBotJobName("Existing");
        split.setProjectType("iOS");

        service.enrich(split);

        assertEquals("Existing", split.getBotJobName());
        assertEquals("iOS", split.getProjectType());
    }

    @Test
    void ignoresMissingSplitOrBotJobId() {
        RecordingData data = new RecordingData();
        ScannerTestMessageMetadataService service = new ScannerTestMessageMetadataService(data);
        SplitDTO split = new SplitDTO();

        service.enrich(null);
        service.enrich(split);

        assertNull(split.getProjectType());
    }

    private static BotJobLoadDTO job(int id, String name, String priority) {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(id);
        job.setName(name);
        job.setPriority(priority);
        return job;
    }

    private static final class RecordingData implements ScannerTestMessageMetadataService.DataPort {
        private final List<BotJobLoadDTO> jobs = new ArrayList<>();

        @Override
        public List<BotJobLoadDTO> botJobs() {
            return jobs;
        }
    }
}
