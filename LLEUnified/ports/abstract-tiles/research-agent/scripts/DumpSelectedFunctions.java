// Ghidra headless helper: dump selected functions by address.
// @category LLE

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class DumpSelectedFunctions extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: DumpSelectedFunctions <output-file> <address>...");
        }
        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);
        if (!decompiler.openProgram(currentProgram)) {
            throw new IllegalStateException("failed to open program in decompiler");
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
                writer.write("signature=" + function.getSignature() + "\n");
                DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
                writer.write("decompileCompleted=" + result.decompileCompleted() + "\n");
                if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
                    writer.write("error=" + result.getErrorMessage() + "\n");
                }
                if (result.getDecompiledFunction() != null) {
                    writer.write(result.getDecompiledFunction().getC());
                    writer.write("\n");
                }
            }
        } finally {
            decompiler.dispose();
        }
    }
}
