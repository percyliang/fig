inheritClass(FileViewTS, ItemTS);

function FileViewTS(table, block) {
  ItemTS.call(this, table, block);
  this.addAction([this.doCmd, true],       "_: Execute command (inline)", "`");
  this.addAction([this.doCmd, false],      "_: Execute command (new window)", "SHIFT-!");
  this.addAction(this.doSummary,           "_: Summary");
  this.addAction([this.doDownload, true],  "_: Download (inline)", "SHIFT-D");
  this.addAction([this.doDownload, false], "_: Download (new window)", "CTRL-SHIFT-D");
}

var CLASS = FileViewTS.prototype;

CLASS.getShowType = function(inline) { return inline ? "msgPanel" : "newWindow"; } 

CLASS.doCmd = function(inline) {
  var cmd = prompt("Command to execute (reads file from stdin)?");
  if(!cmd) return;
  var request = this.newChildRequestWithCurrItem("cmd");
  request.cmd = cmd;
  this.sendMetaRequest({ type : this.getShowType(inline), request : request });
}

CLASS.doSummary = function() {
  var request = this.newChildRequestWithCurrItem("summary");
  this.sendRequest(request);
}

CLASS.doDownload = function(inline) {
  var request = this.newChildRequestWithCurrItem("download");
  this.sendMetaRequest({ type : this.getShowType(inline), request : request });
}
