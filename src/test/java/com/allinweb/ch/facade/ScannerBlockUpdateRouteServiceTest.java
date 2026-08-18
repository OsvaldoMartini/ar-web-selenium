package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class ScannerBlockUpdateRouteServiceTest {

    @Test
    void resolvesBotJobBlockUpdatesFromCurrentJob() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();
        BlockMoveDTO move = new BlockMoveDTO();
        move.setSessionId(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        move.setBotJobId(99);
        BotJobLoadDTO currentJob = job(42, 7);

        ScannerBlockUpdateRouteService.Result result =
                service.resolve(ScannerWorkspaceOperations.UPDATE_BLOCKS, move, currentJob, null);

        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS, result.updateOperation());
        assertEquals("block", result.blockTable());
        assertEquals(42, result.whereId());
        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS, result.previousBlock());
    }

    @Test
    void fallsBackToMoveBotJobWhenCurrentJobHasNoId() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();
        BlockMoveDTO move = new BlockMoveDTO();
        move.setSessionId(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        move.setBotJobId(99);
        BotJobLoadDTO currentJob = job(null, 7);

        ScannerBlockUpdateRouteService.Result result =
                service.resolve(ScannerWorkspaceOperations.UPDATE_BLOCKS, move, currentJob, "stale");

        assertEquals(99, result.whereId());
        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS, result.previousBlock());
    }

    @Test
    void resolvesComponentBlockUpdatesFromCurrentHomeBanking() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();
        BlockMoveDTO move = new BlockMoveDTO();
        move.setSessionId(ScannerWorkspaceSessions.COMPONENT_TASKS);
        move.setHomeBankingId(9);
        BotJobLoadDTO currentJob = job(42, 7);

        ScannerBlockUpdateRouteService.Result result =
                service.resolve(ScannerWorkspaceOperations.UPDATE_BLOCKS, move, currentJob, null);

        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP, result.updateOperation());
        assertEquals("component_block", result.blockTable());
        assertEquals(7, result.whereId());
        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP, result.previousBlock());
    }

    @Test
    void fallsBackToMoveHomeBankingForComponentBlocks() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();
        BlockMoveDTO move = new BlockMoveDTO();
        move.setSessionId(ScannerWorkspaceSessions.COMPONENT_TASKS);
        move.setHomeBankingId(9);
        BotJobLoadDTO currentJob = job(42, null);

        ScannerBlockUpdateRouteService.Result result =
                service.resolve(ScannerWorkspaceOperations.UPDATE_BLOCKS, move, currentJob, null);

        assertEquals(9, result.whereId());
    }

    @Test
    void returnsMinusOneWhenNoOwnerIdExists() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();
        BlockMoveDTO move = new BlockMoveDTO();
        move.setSessionId(ScannerWorkspaceSessions.BOT_JOB_TASKS);

        ScannerBlockUpdateRouteService.Result result =
                service.resolve(ScannerWorkspaceOperations.UPDATE_BLOCKS, move, null, null);

        assertEquals(-1, result.whereId());
    }

    @Test
    void transitionsPreviousBlockForBotJobSessions() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();

        assertEquals(
                ScannerWorkspaceOperations.UPDATE_BLOCKS,
                service.transitionPreviousBlockForSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, null));
        assertEquals(
                ScannerWorkspaceOperations.UPDATE_BLOCKS,
                service.transitionPreviousBlockForSession(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS, ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP));
    }

    @Test
    void transitionsPreviousBlockForComponentSessions() {
        ScannerBlockUpdateRouteService service = new ScannerBlockUpdateRouteService();

        assertEquals(
                ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP,
                service.transitionPreviousBlockForSession(ScannerWorkspaceSessions.COMPONENT_TASKS, null));
        assertEquals(
                ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP,
                service.transitionPreviousBlockForSession(
                        ScannerWorkspaceSessions.COMPONENT_TASKS, ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP));
    }

    private static BotJobLoadDTO job(Integer botJobId, Integer homeBankingId) {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(botJobId);
        job.setHomeBankingId(homeBankingId);
        return job;
    }
}
