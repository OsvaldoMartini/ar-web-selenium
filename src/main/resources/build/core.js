let wasmExports = null;

let wasmMemory = new WebAssembly.Memory({ initial: 256, maximum: 256 });

let wasmTable = new WebAssembly.Table({
  initial: 0, // or 1 if needed immediately
  maximum: 100, // optional
  element: "anyfunc", // required type
});

const wasmImports = {
  env: {
    memory: wasmMemory,
    table: wasmTable,
    // Add any other required imports like console logging, malloc, etc.
    // Example stub:
    abort: () => console.error("WASM abort called"),
    // ... other necessary functions for your C code
  },
  // Optional: include wasi if used in your C code
  wasi_snapshot_preview1: {},
};

async function loadWasm() {
  const response = await fetch("functions.wasm");
  const bytes = await response.arrayBuffer();

  const wasmObj = await WebAssembly.instantiate(bytes, wasmImports);
  wasmExports = wasmObj.instance.exports;

  console.log("WASM loaded, exports:", wasmExports);
}

loadWasm();
