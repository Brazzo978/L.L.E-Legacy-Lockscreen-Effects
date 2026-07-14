// Ghidra headless helper for the canonical ARM32 Watercolor reference.
// @category LLE64.Watercolor

import java.io.File;
import java.io.PrintWriter;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;

public class DumpSelectedFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
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

        File output = new File(args[0]);
        output.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(output, "UTF-8")) {
            writer.println("program=" + currentProgram.getName());
            writer.println("imageBase=" + currentProgram.getImageBase());
            writer.println();

            for (int i = 1; i < args.length; i++) {
                Address address = toAddr(args[i]);
                Function function = getFunctionAt(address);
                if (function == null) {
                    function = getFunctionContaining(address);
                }
                if (function == null) {
                    function = createFunction(address, null);
                }

                writer.println("============================================================");
                writer.println("requested=" + args[i]);
                if (function == null) {
                    writer.println("NO FUNCTION");
                    writer.println();
                    continue;
                }

                writer.println("entry=" + function.getEntryPoint());
                writer.println("name=" + function.getName());
                writer.println("signature=" + function.getSignature());
                writer.println("body=" + function.getBody());
                writer.println("references-to:");
                ReferenceIterator references = currentProgram.getReferenceManager()
                        .getReferencesTo(function.getEntryPoint());
                while (references.hasNext()) {
                    Reference reference = references.next();
                    writer.println("  " + reference.getFromAddress() + " "
                            + reference.getReferenceType());
                }

                DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
                writer.println("decompileCompleted=" + result.decompileCompleted());
                writer.println("error=" + result.getErrorMessage());
                if (result.getDecompiledFunction() != null) {
                    writer.println(result.getDecompiledFunction().getC());
                }
                writer.println();
            }
        } finally {
            decompiler.dispose();
        }
    }
}
