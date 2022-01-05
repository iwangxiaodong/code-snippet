
//  https://github.com/konsoletyper/teavm/blob/master/core/src/main/resources/org/teavm/backend/wasm/wasm-runtime.js
var wasmImports = {teavmHeapTrace: {init: (p) => {
        }}, teavm: {
        logString: (p) => {
        }, logInt: (p) => {
        }, logOutOfMemory: (p) => {
        },
        currentTimeMillis: (p) => new Date().getTime()}
};

function returnStringResult(stringPtr, wasmInstance) {
    let arrayPtr = wasmInstance.exports.teavm_stringData(stringPtr);
    let length = wasmInstance.exports.teavm_arrayLength(arrayPtr);
    let cad = wasmInstance.exports.teavm_charArrayData(arrayPtr);
    let mb = wasmInstance.exports.memory.buffer;
    let arrayData = new DataView(mb, cad, length * 2);
    var text = length > 0 ? "" : null;
    for (let i = 0; i < length; ++i) { //从对象数据区读起
        let charCode = arrayData.getUint16(i * 2, true);
        text += String.fromCharCode(charCode);
    }
    return text;
}

function newStringParam(stringValue, wasmInstance) {
    let stringPtr = wasmInstance.exports.teavm_allocateString(stringValue.length);
    let arrayPtr = wasmInstance.exports.teavm_objectArrayData(
            wasmInstance.exports.teavm_stringData(stringPtr));
    let arrayData = new DataView(wasmInstance.exports.memory.buffer,
            arrayPtr, stringValue.length * 2);
    for (let j = 0; j < stringValue.length; ++j) {
        arrayData.setUint16(j * 2, stringValue.charCodeAt(j), true);
    }
    return stringPtr;
}

fetch('http://localhost:8080/releases/classes.wasm')
        .then(r => r.arrayBuffer()).then(r => {
    var rr = new Response(r, {headers: {'Content-Type': 'application/wasm'}});
    WebAssembly.instantiateStreaming(rr, wasmImports)
            .then(obj => {
                var rs = obj.instance.exports.process(newStringParam("Hi你好", obj.instance));
                alert(returnStringResult(rs, obj.instance));
            })
            .catch((err) => console.log(err));
});
