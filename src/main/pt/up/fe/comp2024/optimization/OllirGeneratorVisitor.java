package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {


    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final JmmSymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public
    OllirGeneratorVisitor(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
        exprVisitor = new OllirExprGeneratorVisitor(this.table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(VAR_DECL, this::visitVar);
        addVisit(IMPORT_DECLARATION, this::visitImport);
        addVisit(EXPR_STMT, this::visitExprStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();

        String i = node.getAncestor(CLASS_DECL).map(method -> method.get("className")).orElseThrow();

        if(node.getChild(1).hasAttribute("name") && node.getChild(1).get("name").equals(i)){
            code.append(node.getChild(0).get("name"));
            code.append(".");
            code.append(node.getChild(1).get("name"));
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(".");
            code.append(node.getChild(1).get("name"));
            code.append(SPACE);
            code.append("new(");
            code.append(node.getChild(1).get("name"));
            code.append(")");
            code.append(".");
            code.append(node.getChild(1).get("name"));
            code.append(END_STMT);
            code.append(NL);
            code.append("invokespecial(");
            code.append(node.getChild(0).get("name"));
            code.append(".");
            code.append(node.getChild(1).get("name"));
            code.append(", \"<init>\").V;");
            code.append(NL);
            return code.toString();
        }


        var lhs = exprVisitor.visit(node.getChild(0));
        var rhs = exprVisitor.visit(node.getChild(1));
        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        if(node.getChild(1).getKind().equals("Not")){
            code.append("!");
            code.append(typeString);
            code.append(SPACE);
            code.append(node.getChild(1).getChild(0).get("name"));
            code.append(typeString);
            code.append(SPACE);
            //code.append("t2");
            //code.append(typeString);
        }

        if(node.getChild(1).getKind().equals("ArrayDecl")){
            code.append("new(array");
            code.append(", ");
            code.append(node.getChild(1).getChild(0).get("name"));
            code.append(typeString);
            code.append(").array");
            code.append(typeString);
            //code.append("t2");
            //code.append(typeString);
        }

        if(node.getJmmChild(1).getKind().equals("VarDeclaration")){
            code.append("new(");
            code.append(node.getJmmChild(1).get("name"));
            code.append(").");
            code.append(node.getJmmChild(1).get("name"));
            code.append(";");
            code.append("\n");
            //call invokespecial with the following format: invokespecial(a.A, "<init>").V; where a is the variable name and A the class name
            code.append("invokespecial(")
                    .append(node.getJmmChild(0).get("name"))
                    .append(".")
                    .append(node.getJmmChild(1).get("name"))
                    .append(", \"<init>\").V");
        }

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
            String op = node.get("name");
            String firstoperand = (node.getChild(0).get("name"));
            String secondoperand = (node.getChild(1).get("name"));
            code.append(OptUtils.getTemp()).append(OptUtils.toOllirType(retType)).append(" :=").append(OptUtils.toOllirType(retType)).append(SPACE).append(firstoperand).append(OptUtils.toOllirType(retType)).append(SPACE).append(op).append(OptUtils.toOllirType(retType)).append(SPACE).append(secondoperand).append(OptUtils.toOllirType(retType)).append(";\n");
            code.append("ret").append(OptUtils.toOllirType(retType));
            code.append(SPACE);
            code.append(OptUtils.getTemp2()).append(OptUtils.toOllirType(retType));
            code.append(";\n");
            return code.toString();

        }

        else if(node.getAttributes().contains("value")){
            code.append("ret");
            code.append(OptUtils.toOllirType(retType));
            code.append(SPACE);
            code.append(node.get(("value")));
            code.append(OptUtils.toOllirType(retType));
            code.append(END_STMT);
            return code.toString();
        }

        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);
        code.append(node.get(("name")));
        code.append(OptUtils.toOllirType(retType));
        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var tipo = node.getJmmChild(0).get("name");
        var id = node.get("name");

        return id + OptUtils.toOllirType2(tipo);
    }



    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        var name = node.get("name");

        if(node.get("name").equals("main")){
            code.append("static ");
            code.append("main(");
            code.append("args.array.String");
            code.append(").V");
            code.append(L_BRACKET);
            for(var i: node.getChildren()){
                if (i.getKind().equals("VarDecl")) {
                    continue;
                }

                else if (i.getKind().equals("Param")) {
                    continue;
                }

                else {
                    code.append(visit(i, unused));
                }
            }
            code.append("ret.V; \n");
            code.append(R_BRACKET);
            code.append(NL);
            return code.toString();
        }

        else {
            code.append(name);
            code.append("(");
            List <JmmNode> params = node.getChildren("Param");
            String p = params.stream().map(param -> visit(param,unused)).collect(Collectors.joining(", "));
            code.append(p);

            code.append(")");
            code.append(OptUtils.toOllirType(table.getReturnType(node.get("name"))));
            code.append(L_BRACKET);
        }

        if(node.getNumChildren()==0){
            code.append("ret.V; \n");
        }

        else if(node.getNumChildren()<2){
            code.append(visit(node.getChild(0), unused));
            code.append("ret.V; \n");
        }

        else if(node.getNumChildren()>1 && node.get("name").equals("main")){
            code.append(visit(node.getChild(0), unused));
        }

        List<JmmNode> assigns = node.getChildren("AssignStmt");

        List<String> assignvars = new ArrayList<>();

        for(var i : assigns){
            assignvars.add(i.getChild(0).get("name"));
        }


        String a = node.getChild(0).get("name");

        for(int i=1;i<node.getChildren().size();i++) {

            if (node.getChild(i).getKind().equals("VarDecl") && ((assignvars != null) && assignvars.contains(node.getChild(i).get("name")))) {
                continue;
            }

            else if (node.getChild(i).getKind().equals("Param")) {
                continue;
            }

            else if(i==node.getNumChildren()-1) {
                code.append(visitReturn(node.getChild(i), unused));
            }

            else code.append(visit(node.getChild(i), unused));
        }

        code.append(R_BRACKET);
        code.append(NL);
        return code.toString();

    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        /*for (var importn : table.getImports()) {
            code.append("import ");
            code.append(importn + ";\n");
        }*/

        code.append(table.getClassName());

        var superClass = table.getSuper();
        boolean existssuperclass = false;
        if (!superClass.equals("")) {
            existssuperclass = true;
            code.append(" extends ").append(superClass);
        }
        code.append(L_BRACKET);

        code.append(NL);

        List<Symbol> fields = table.getFields();

        for (var field :fields) {
            code.append(".field public ");
            code.append(field.getName());
            code.append(OptUtils.toOllirType(field.getType()));
            code.append(";\n");
        }

        code.append(buildConstructor());

        var needNl = true;

        for (var child : node.getChildren()) {

            if (child.getKind().equals("VarDecl")) {
                continue;
            }
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitVar(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(node.get("name"));
        var tipo = node.getJmmChild(0).get("name");
        code.append(OptUtils.toOllirType2(tipo));

        code.append(SPACE);
        code.append(":=");
        code.append(OptUtils.toOllirType2(tipo));
        code.append(SPACE);
        code.append(OptUtils.getTemp());
        code.append(OptUtils.toOllirType2(tipo));


        code.append(";\n");


        return code.toString();
    }

    private String visitImport(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var importName = node.getObjectAsList("name");

        var importNameString = importName.stream().map(Object::toString).collect(Collectors.joining("."));

        code.append("import ");
        code.append(importNameString);
        code.append(END_STMT);

        return code.toString();
    }

    public String visitExprStmt(JmmNode node, Void unused) {
        var computation = exprVisitor.visit(node.getJmmChild(0)).getComputation();

        return computation.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }




}
