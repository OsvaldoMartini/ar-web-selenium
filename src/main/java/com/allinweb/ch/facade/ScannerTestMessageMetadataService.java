package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.SplitDTO;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Enriches scanner test messages with bot-job metadata needed for routing. */
public final class ScannerTestMessageMetadataService {
    private final DataPort data;

    public ScannerTestMessageMetadataService(DataPort data) {
        this.data = data;
    }

    public void enrich(SplitDTO splitDTO) {
        if (splitDTO == null || splitDTO.getBotJobId() == null) {
            return;
        }

        for (BotJobLoadDTO job : data.botJobs()) {
            if (job != null && Objects.equals(job.getId(), splitDTO.getBotJobId())) {
                splitDTO.setBotJobName(job.getName());
                splitDTO.setProjectType(job.getPriority());
                return;
            }
        }
    }

    public interface DataPort {
        List<BotJobLoadDTO> botJobs();
    }

    public static final class DefaultDataPort implements DataPort {
        private final PerformLists lists = PerformLists.getInstance();

        @Override
        public List<BotJobLoadDTO> botJobs() {
            List<BotJobLoadDTO> jobs = lists.getListBotJob();
            return jobs == null ? Collections.emptyList() : jobs;
        }
    }
}
