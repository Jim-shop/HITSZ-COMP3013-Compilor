package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.Objects;
import java.util.Stack;

// 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private final Stack<Token> tokenStack = new Stack<>();
    private SymbolTable symbolTable = null;

    @Override
    public void whenAccept(Status currentStatus) {
        // 该过程在遇到 Accept 时要采取的代码动作
        // Nothing to do.
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // 该过程在遇到 reduce production 时要采取的代码动作
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
                // Set symbol type.
                final var id = tokenStack.pop();
                final var d = tokenStack.pop();
                final var symbol = symbolTable.get(id.getText());
                if (symbol.getType() != null) { // Define a symbol multiple time.
                    throw new RuntimeException("Redefine");
                }
                assert Objects.equals(d.getKind().getIdentifier(), "Int");
                symbol.setType(SourceCodeType.Int);
                tokenStack.push(null); // placeholder
            }
            case 5 -> { // D -> int;
                // Hold the element
            }
            case 6 -> { // S -> id = E;
                // Check if id not define.
                tokenStack.pop();
                tokenStack.pop();
                final var id = tokenStack.pop();
                final var symbol = symbolTable.get(id.getText());
                if (symbol.getType() == null) {
                    throw new RuntimeException("Redefine");
                }
                tokenStack.push(null); // placeholder
            }
            case 7 -> { // S -> return E;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 8 -> { // E -> E + A;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            case 9 -> { // E -> E - A;
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.pop();
                tokenStack.push(null); // placeholder
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
                // Check if id not define.
                final var id = tokenStack.peek();
                final var symbol = symbolTable.get(id.getText());
                if (symbol.getType() == null) {
                    throw new RuntimeException("Redefine");
                }
                // Replace with a placeholder.
                tokenStack.pop();
                tokenStack.push(null);
            }
            case 15 -> { // B -> IntConst;
                tokenStack.pop();
                tokenStack.push(null); // placeholder
            }
            default -> {
                throw new RuntimeException("Unknown production index");
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // 该过程在遇到 shift 时要采取的代码动作
        tokenStack.push(currentToken);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.symbolTable = table;
    }
}

