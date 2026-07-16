// Ghidra headless helper: locate instructions containing selected scalar operands.
// @category LLE

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.scalar.Scalar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

public class FindScalarInstructionContext extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: FindScalarInstructionContext <output-file> <scalar>...");
        }
        Set<Long> wanted = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            wanted.add(Long.decode(args[i]));
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(args[0])))) {
            InstructionIterator instructions = currentProgram.getListing().getInstructions(true);
            while (instructions.hasNext() && !monitor.isCancelled()) {
                Instruction instruction = instructions.next();
                boolean matches = false;
                for (int operand = 0; operand < instruction.getNumOperands(); operand++) {
                    Scalar scalar = instruction.getScalar(operand);
                    if (scalar != null && wanted.contains(scalar.getUnsignedValue())) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
                Address address = instruction.getAddress();
                Function function = currentProgram.getFunctionManager().getFunctionContaining(address);
                writer.write("\n============================================================\n");
                writer.write("match=" + address + "  " + instruction + "\n");
                writer.write("function=" + (function == null ? "<none>" :
                        function.getName(true) + " " + function.getBody()) + "\n");
                Instruction cursor = instruction;
                for (int i = 0; i < 12 && cursor.getPrevious() != null; i++) {
                    cursor = cursor.getPrevious();
                }
                for (int i = 0; i < 25 && cursor != null; i++) {
                    writer.write((cursor.getAddress().equals(address) ? "> " : "  ")
                            + cursor.getAddress() + "  " + cursor + "\n");
                    cursor = cursor.getNext();
                }
            }
        }
    }
}
