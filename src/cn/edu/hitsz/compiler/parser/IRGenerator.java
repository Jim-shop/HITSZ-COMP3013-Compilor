package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {

    private final Stack<IRValue> valueStack = new Stack<>();
    private final Stack<Token> tokenStack = new Stack<>();
    private final List<Instruction> ir = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        tokenStack.push(currentToken);
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        switch (production.index()) {
            case 1 -> { // P -> S_list;
                tokenStack.push(null); // placeholder
            }
            case 2 -> { // S_list -> S Semicolon S_list;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 3 -> { // S_list -> S Semicolon;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 4 -> { // S -> D id;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 5 -> { // D -> int;
                // Hold the element
            }
            case 6 -> { // S -> id = E;
                tokenStack.pop();
                tokenStack.pop();
                final var id = tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var e = valueStack.pop();
                final var dst = IRVariable.named(id.getText());
                ir.add(Instruction.createMov(dst, e));
            }
            case 7 -> { // S -> return E;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var e = valueStack.pop();
                ir.add(Instruction.createRet(e));
            }
            case 8 -> { // E -> E + A;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var a = valueStack.pop();
                final var e = valueStack.pop();
                final var dst = IRVariable.temp();
                valueStack.push(dst);
                ir.add(Instruction.createAdd(dst, e, a));
            }
            case 9 -> { // E -> E - A;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var a = valueStack.pop();
                final var e = valueStack.pop();
                final var dst = IRVariable.temp();
                valueStack.push(dst);
                ir.add(Instruction.createSub(dst, e, a));
            }
            case 10 -> { // E -> A;
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 11 -> { // A -> A * B;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var b = valueStack.pop();
                final var a = valueStack.pop();
                final var dst = IRVariable.temp();
                valueStack.push(dst);
                ir.add(Instruction.createMul(dst, a, b));
            }
            case 12 -> { // A -> B;
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 13 -> { // B -> ( E );
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 14 -> { // B -> id;
                final var id = tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var src = IRVariable.named(id.getText());
                valueStack.push(src);
            }
            case 15 -> { // B -> IntConst;
                final var intConst = tokenStack.pop();
                tokenStack.push(null); // placeholder
                final var imm = IRImmediate.of(Integer.parseInt(intConst.getText()));
                valueStack.push(imm);
            }
            default -> throw new RuntimeException("Unknown production index");
        }
    }


    @Override
    public void whenAccept(Status currentStatus) {
        // Do nothing.
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
    }

    public List<Instruction> getIR() {
        return ir;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

