var fs = require("fs")
var buf = fs.readFileSync("words.txt", {encoding: "utf-8"})
var arr = buf.split(",")
// arr.sort((a, b) => {
//     return a.localeCompare(b);
// })
console.log(arr.length)
