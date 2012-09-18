inheritClass(ExecViewTS, ItemTS);

function ExecViewTS(table, block) {
  ItemTS.call(this, table, block);
  this.addAction(this.doKill,         "K: Kill checked execs");
  this.addAction(this.doQueueThunks,  "Q: Queue checked execs to run");
  this.addAction(this.doReloadItems,  "R: Reload all execs here");
  this.addAction(this.doStripExtCollectCheckedItems, "C: Collect/print no-ext checked items");
}

var CLASS = ExecViewTS.prototype;

CLASS.doStripExtCollectCheckedItems = function() {
  var names = this.getCheckedItemNames();
  for(var i = 0; i < names.length; i++)
    names[i] = names[i].replace(".exec", "");
  this.showMsg(verbatim(names.join(",")));
}

CLASS.doKill = function() {
  this.sendRequestWithCheckedItems("Kill checked execs?", "kill");
}

// Can implement when regulary copy/paste commands
CLASS.doQueueThunks = function() {
  var items = this.getCheckedItemNames();
  if(items.length == 0) return;
  var request = this.newChildRequest("copyItem", items);
  request.destTrail = "workers\treadyExecs";
  this.sendRequest(request);
}

CLASS.doReloadItems = function() {
  var request = this.newChildRequest("reload", this.getAllItemNames());
  return this.sendRequest(request, this.reloadIfSuccessHandler());
}

////////////////////////////////////////////////////////////

inheritClass(WorkerViewTS, ItemTS);

function WorkerViewTS(table, block) {
  ItemTS.call(this, table, block);
  this.addAction(this.doKill, "K: Kill job on checked workers");
  this.addAction(this.doTerminate, "T: Terminate checked workers");
}

var CLASS = WorkerViewTS.prototype;

CLASS.doKill = function() {
  this.sendRequestWithCheckedItems("Kill job on checked workers?", "kill");
}

CLASS.doTerminate = function() {
  this.sendRequestWithCheckedItems("Terminate checked workers?", "terminate");
}
