// Sample data (mocketData)
export interface BlockLoopInstructionLoadDTO {
  id: number;
  instructionOrderNumber: number;
  name: string;
  description: string;
  blockId: number;
  blockName: string;
  instructionType: string;
}

const instructionsMockData: BlockLoopInstructionLoadDTO[] = [
  { id: 1, instructionOrderNumber: 1, name: "Instruction 1", description: "Description 1", blockId: 1, blockName: "Default Block", instructionType: "SET" },
  { id: 2, instructionOrderNumber: 4, name: "Instruction 2", description: "Description 2", blockId: 2, blockName: "Block Test 1", instructionType: "SET" },
  { id: 3, instructionOrderNumber: 3, name: "Instruction 3", description: "Description 3", blockId: 2, blockName: "Block Test 1", instructionType: "GET" },
  { id: 4, instructionOrderNumber: 2, name: "Instruction 4", description: "Description 4", blockId: 2, blockName: "Block Test 1", instructionType: "CK" },
  { id: 5, instructionOrderNumber: 1, name: "Instruction 5", description: "Description 5", blockId: 2, blockName: "Block Test 1", instructionType: "SET" },
  { id: 6, instructionOrderNumber: 2, name: "Instruction 6", description: "Description 6", blockId: 3, blockName: "Block Test 2", instructionType: "SET" },
  { id: 7, instructionOrderNumber: 1, name: "Instruction 7", description: "Description 7", blockId: 3, blockName: "Block Test 2", instructionType: "GET" },

  // Block 3
  { id: 8, instructionOrderNumber: 1, name: "Instruction 8", description: "Description 8", blockId: 4, blockName: "Block Test 3", instructionType: "SET" },
  { id: 9, instructionOrderNumber: 2, name: "Instruction 9", description: "Description 9", blockId: 4, blockName: "Block Test 3", instructionType: "GET" },
  { id: 10, instructionOrderNumber: 3, name: "Instruction 10", description: "Description 10", blockId: 4, blockName: "Block Test 3", instructionType: "CK" },
  { id: 11, instructionOrderNumber: 4, name: "Instruction 11", description: "Description 11", blockId: 4, blockName: "Block Test 3", instructionType: "SET" },
  { id: 12, instructionOrderNumber: 5, name: "Instruction 12", description: "Description 12", blockId: 4, blockName: "Block Test 3", instructionType: "GET" },

  // Block 4
  { id: 13, instructionOrderNumber: 1, name: "Instruction 13", description: "Description 13", blockId: 5, blockName: "Block Test 4", instructionType: "CK" },
  { id: 14, instructionOrderNumber: 2, name: "Instruction 14", description: "Description 14", blockId: 5, blockName: "Block Test 4", instructionType: "SET" },
  { id: 15, instructionOrderNumber: 3, name: "Instruction 15", description: "Description 15", blockId: 5, blockName: "Block Test 4", instructionType: "GET" },
  { id: 16, instructionOrderNumber: 4, name: "Instruction 16", description: "Description 16", blockId: 5, blockName: "Block Test 4", instructionType: "CK" },
  { id: 17, instructionOrderNumber: 5, name: "Instruction 17", description: "Description 17", blockId: 5, blockName: "Block Test 4", instructionType: "SET" },

  // Block 5
  { id: 18, instructionOrderNumber: 1, name: "Instruction 18", description: "Description 18", blockId: 6, blockName: "Block Test 5", instructionType: "GET" },
  { id: 19, instructionOrderNumber: 2, name: "Instruction 19", description: "Description 19", blockId: 6, blockName: "Block Test 5", instructionType: "SET" },
  { id: 20, instructionOrderNumber: 3, name: "Instruction 20", description: "Description 20", blockId: 6, blockName: "Block Test 5", instructionType: "CK" },
  { id: 21, instructionOrderNumber: 4, name: "Instruction 21", description: "Description 21", blockId: 6, blockName: "Block Test 5", instructionType: "SET" },
  { id: 22, instructionOrderNumber: 5, name: "Instruction 22", description: "Description 22", blockId: 6, blockName: "Block Test 5", instructionType: "GET" },

  // Block 6
  { id: 23, instructionOrderNumber: 1, name: "Instruction 23", description: "Description 23", blockId: 7, blockName: "Block Test 6", instructionType: "CK" },
  { id: 24, instructionOrderNumber: 2, name: "Instruction 24", description: "Description 24", blockId: 7, blockName: "Block Test 6", instructionType: "SET" },
  { id: 25, instructionOrderNumber: 3, name: "Instruction 25", description: "Description 25", blockId: 7, blockName: "Block Test 6", instructionType: "GET" },
  { id: 26, instructionOrderNumber: 4, name: "Instruction 26", description: "Description 26", blockId: 7, blockName: "Block Test 6", instructionType: "CK" },
  { id: 27, instructionOrderNumber: 5, name: "Instruction 27", description: "Description 27", blockId: 7, blockName: "Block Test 6", instructionType: "SET" },

  // Block 7
  { id: 28, instructionOrderNumber: 1, name: "Instruction 28", description: "Description 28", blockId: 8, blockName: "Block Test 7", instructionType: "GET" },
  { id: 29, instructionOrderNumber: 2, name: "Instruction 29", description: "Description 29", blockId: 8, blockName: "Block Test 7", instructionType: "SET" },
  { id: 30, instructionOrderNumber: 3, name: "Instruction 30", description: "Description 30", blockId: 8, blockName: "Block Test 7", instructionType: "CK" },
  { id: 31, instructionOrderNumber: 4, name: "Instruction 31", description: "Description 31", blockId: 8, blockName: "Block Test 7", instructionType: "SET" },
  { id: 32, instructionOrderNumber: 5, name: "Instruction 32", description: "Description 32", blockId: 8, blockName: "Block Test 7", instructionType: "GET" }
];

export default instructionsMockData;