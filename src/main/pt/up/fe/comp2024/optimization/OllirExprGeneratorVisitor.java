package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.specs.util.SpecsSystem;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final JmmSymbolTable table;

    public OllirExprGeneratorVisitor(JmmSymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_OP, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(FUNCTION_CALL, this::visitFunctionCall);
        addVisit(PARENTHESIS, this::visitParenthesis);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitParenthesis(JmmNode paren, Void unused) {
        return visit(paren.getJmmChild(0));
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String tipo = OptUtils.toOllirType(intType);
        String code = node.get("name") + tipo;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("name")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation.toString());
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        
        var id = node.get("name");
        Type tipo = TypeUtils.getExprType(node, table);
        String a = tipo.getName();


        String typeCode = OptUtils.toOllirType2(a);

        String code = id + typeCode;
        return new OllirExprResult(code);
    }

    public OllirExprResult visitFunctionCall(JmmNode functionCall, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        OllirExprResult caller = visit(functionCall.getJmmChild(0));
        computation.append(caller.getComputation());

        String returnType;
        var imports = table.getImports();
        var getImports = imports.stream().map(imp -> imp.substring(imp.lastIndexOf(".") + 1)).toList();

        String callerName = functionCall.getJmmChild(0).get("name");

        if(getImports.contains(callerName)){
            if(ASSIGN_STMT.check(functionCall.getParent())){
                var leftOp = functionCall.getParent().getJmmChild(0);
                returnType = OptUtils.toOllirType(TypeUtils.getExprType(leftOp, table));
            }else{
                returnType = ".V";
            }
        } else{
            returnType = OptUtils.toOllirType(table.getReturnType(functionCall.get("name")));
        }

        List<OllirExprResult> parameters = new ArrayList<>();
        for (int i = 1; i < functionCall.getNumChildren(); i++) {
            OllirExprResult parameter = visit(functionCall.getJmmChild(i));
            parameters.add(parameter);
            computation.append(parameter.getComputation());
        }

        if(!returnType.equals(".V")){
            String tempVar = OptUtils.getTemp();
            code.append(tempVar).append(returnType);
            computation.append(code)
                    .append(" :=")
                    .append(returnType)
                    .append(" ");
        }


        var methodsStatic = table.getStaticMethods();

        if(methodsStatic.contains(callerName) || getImports.contains(callerName)){
            computation.append("invokestatic(");
            computation.append(callerName);
        }else{
            computation.append("invokevirtual(");
            computation.append(callerName);
            if (callerName.equals("this")) {
                computation.append(".").append(table.getClassName());
            }
            //check if caller is an object
            else {
                computation.append(".").append(table.getClassName());
            }

        }

        computation.append(", \"").append(functionCall.get("name")).append("\"");
        for (OllirExprResult parameter : parameters) {
            computation.append(", ").append(parameter.getCode());
        }
        computation.append(")");


        computation.append(returnType);
        computation.append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
