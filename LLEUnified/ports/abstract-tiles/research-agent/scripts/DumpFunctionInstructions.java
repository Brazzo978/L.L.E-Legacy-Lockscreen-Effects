// Ghidra headless helper: dump raw instructions for selected functions.
// @category LLE

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class DumpFunctionInstructions extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: DumpFunctionInstructions <output-file> <address>...");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(args[0])))) {
            for (int i = 1; i < args.length; i++) {
                Address address = toAddr(args[i]);
                Function function = currentProgram.getFunctionManager().getFunctionContaining(address);
                writer.write("\n============================================================\n");
                writer.write("requested=" + address + "\n");
                if (function == null) {
                    writer.write("function=NOT_FOUND\n");
                    continue;
                }
                writer.write("entry=" + function.getEntryPoint() + "\n");
                writer.write("name=" + function.getName(true) + "\n");
                writer.write("body=" + function.getBody() + "\n");
                InstructionIterator instructions =
                        currentProgram.getListing().getInstructions(function.getBody(), true);
                while (instructions.hasNext()) {
                    Instruction instruction = instructions.next();
                    writer.write(instruction.getAddress() + "  " + instruction + "\n");
                }
            }
        }
    }
}
