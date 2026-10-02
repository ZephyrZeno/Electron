package top.lacrus.zephyrzeno.electron.assembly;

import java.util.*;

/** EA-8 v0.1: 16-bit instructions (opcode, unsigned 8-bit operand), 256 word ROM. */
public final class Assembler {
    public enum Opcode {
        NOP(0x00, false), LDI(0x01, true), LDA(0x02, true), STA(0x03, true),
        ADD(0x04, true), SUB(0x05, true), AND(0x06, true), OR(0x07, true), XOR(0x08, true),
        JMP(0x09, true), JZ(0x0a, true), JC(0x0b, true), OUT(0x0c, false), HLT(0xff, false);
        public final int code;
        public final boolean operand;
        Opcode(int code, boolean operand) { this.code = code; this.operand = operand; }
    }
    private record Line(int number, String text) {}
    private Assembler() {}

    public static byte[] assemble(String source) {
        Map<String, Integer> labels = new HashMap<>();
        List<Line> instructions = new ArrayList<>();
        String[] lines = source.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String text = lines[i].split(";", 2)[0].trim().toUpperCase(Locale.ROOT);
            if (text.isEmpty()) continue;
            int colon = text.indexOf(':');
            if (colon >= 0) {
                String label = text.substring(0, colon).trim();
                if (!label.matches("[A-Z_][A-Z0-9_]*")) throw error(i + 1, "Invalid label: " + label);
                if (labels.putIfAbsent(label, instructions.size()) != null) throw error(i + 1, "Duplicate label: " + label);
                text = text.substring(colon + 1).trim();
            }
            if (!text.isEmpty()) instructions.add(new Line(i + 1, text));
            if (instructions.size() > 256) throw error(i + 1, "Program exceeds 256 instructions");
        }
        if (instructions.isEmpty()) throw new IllegalArgumentException("Program is empty");
        byte[] bytes = new byte[instructions.size() * 2];
        for (int i = 0; i < instructions.size(); i++) {
            Line line = instructions.get(i);
            String[] parts = line.text().split("\\s+");
            Opcode opcode;
            try { opcode = Opcode.valueOf(parts[0]); }
            catch (IllegalArgumentException e) { throw error(line.number(), "Unknown opcode: " + parts[0]); }
            if (parts.length != (opcode.operand ? 2 : 1)) throw error(line.number(), "Wrong operand count for " + opcode);
            int value = opcode.operand ? operand(parts[1], labels, line.number()) : 0;
            if ((opcode == Opcode.JMP || opcode == Opcode.JZ || opcode == Opcode.JC) && value >= instructions.size())
                throw error(line.number(), "Jump is outside program: " + value);
            bytes[i * 2] = (byte) opcode.code;
            bytes[i * 2 + 1] = (byte) value;
        }
        return bytes;
    }

    private static int operand(String token, Map<String, Integer> labels, int line) {
        if (labels.containsKey(token)) {
            int address = labels.get(token);
            if (address > 255) throw error(line, "Label outside ROM: " + token);
            return address;
        }
        try {
            int value = token.startsWith("0X") ? Integer.parseInt(token.substring(2), 16)
                    : token.startsWith("0B") ? Integer.parseInt(token.substring(2), 2) : Integer.parseInt(token);
            if (value < 0 || value > 255) throw error(line, "Operand must be 0..255: " + token);
            return value;
        } catch (NumberFormatException e) { throw error(line, "Unknown label or invalid number: " + token); }
    }

    private static IllegalArgumentException error(int line, String message) { return new IllegalArgumentException("Line " + line + ": " + message); }

    public static void main(String[] args) throws java.io.IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: Assembler input.ea output.bin");
        byte[] binary = assemble(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])));
        java.nio.file.Files.write(java.nio.file.Path.of(args[1]), binary);
        System.out.println("Assembled " + binary.length / 2 + " EA-8 instructions into " + args[1]);
    }
}
