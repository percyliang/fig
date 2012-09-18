package fig.exec;

import fig.basic.*;

/**
 * Runs any command within the execution framework.
 */
public class ExecWrapper implements Runnable {
  @Option(required=true) public String command;

  public void run() {
    String dir = Execution.getVirtualExecDir();
    if(StrUtils.isEmpty(dir))
      throw Exceptions.bad("No execution directory specified; use the -create flag");
    command = Utils.makeRunCommandInDir(command, dir);
    LogInfo.logs(command);
    Utils.systemHard(command);
  }

  public static void main(String[] args) {
    Execution.run(args, new ExecWrapper());
  }
}
