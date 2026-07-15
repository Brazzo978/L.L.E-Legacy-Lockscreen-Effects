// Ghidra headless helper: dump every function and its decompiler output.
// @category LLE

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class DumpAllFunctions extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: DumpAllFunctions <output-file>");
        }

        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);
        if (!decompiler.openProgram(currentProgram)) {
            throw new IllegalStateException("failed to open program in decompiler");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(args[0])))) {
            FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
            int count = 0;
            while (functions.hasNext() && !monitor.isCancelled()) {
                Function function = functions.next();
                writer.write("\n============================================================\n");
                writer.write("address=" + function.getEntryPoint() + "\n");
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
                count++;
            }
            writer.write("\nfunctionCount=" + count + "\n");
        } finally {
            decompiler.dispose();
        }
    }
}
