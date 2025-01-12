package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;

public class BlockSplitTest {
    public static void main(String[] args) {
        // The JSON string to be parsed
        String json =
                "{\"type\":\"BLOCKS_SPLITTED\",\"details\":{\"originalBlock\":{\"blockId\":1727346858203,\"updatedInstructions\":[{\"instructionId\":391,\"blockId\":1727346858203,\"orderNumber\":1},{\"instructionId\":390,\"blockId\":1727346858203,\"orderNumber\":2},{\"instructionId\":392,\"blockId\":1727346858203,\"orderNumber\":3},{\"instructionId\":387,\"blockId\":1727346858203,\"orderNumber\":4},{\"instructionId\":393,\"blockId\":1727346858203,\"orderNumber\":5},{\"instructionId\":388,\"blockId\":1727346858203,\"orderNumber\":6},{\"instructionId\":394,\"blockId\":1727346858203,\"orderNumber\":7}]},\"newBlock\":{\"botJobId\":11, \"blockId\":1727347345056,\"blockName\":\"Test FNZ default block\",\"blockOrderNumber\":1,\"instructions\":[{\"instructionId\":386,\"blockId\":1727347345056,\"orderNumber\":1},{\"instructionId\":395,\"blockId\":1727347345056,\"orderNumber\":2}]},\"updatedBlocks\":[{\"blockId\":33,\"blockName\":\"Test FNZ default block\",\"blockOrderNumber\":2}]}}";

        // Initialize Gson instance
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Parse the JSON string into the BlockSplitDTO object
        try {
            BlockSplitDTO blockSplitDTO = gson.fromJson(json, BlockSplitDTO.class);

            // Accessing parts of the parsed DTO
            BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
            BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
            List<BlockOrderDetailDTO> updatedBlocks = blockSplitDTO.getDetails().getUpdatedBlocks();

            // Print out the values to verify the parsing
            System.out.println("Original Block ID: " + originalBlock.getBlockId());
            System.out.println("New Block Name: " + newBlock.getBlockName());
            System.out.println("Updated Block Count: " + updatedBlocks.size());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
