package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class TypeDeclarationsVerification extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.PROGRAM, this::visitProgram);
        addVisit(Kind.CLASS_DECL, this::visitClass);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.EXPR_STMT, this::visitExpression);
        addVisit(Kind.ARRAY_INIT, this::visitArrayInit);
        addVisit(Kind.BINARY_OP, this::visitBinaryExpr);
        addVisit(Kind.ACCESS_ARRAY, this::visitAccessArray);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);
        addVisit(Kind.FUNCTION_CALL, this::visitFunctionCall);
        addVisit(Kind.IF, this::visitIf);
        addVisit(Kind.WHILE, this::visitWhile);
        addVisit(Kind.THIS, this::visitThis);
        addVisit(Kind.LENGTH, this::visitLength);
        addVisit(Kind.ASSIGN_ARRAY, this::visitArrayAssign);
        addVisit(Kind.BRACKETS, this::visitBrackets);
        addVisit(Kind.NOT, this::visitNot);
        addVisit(Kind.PARENTHESIS, this::visitParenthesis);
        addVisit(Kind.RETURN_STMT, this::visitReturn);
        addVisit(Kind.VAR_DECLARATION, this::visitVarDecl);
    }

    private Void visitProgram(JmmNode program, SymbolTable table){
        for(JmmNode child : program.getChildren()){
            if(child.getKind().equals("classDecl")){
                visitClass(child, table);
            }
        }

        Set<String> imports = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        for(String importName : table.getImports()){
            if(!imports.add(importName)){
                addTypeError(program, "Duplicate import: " + importName);
                return null;
            }
            String className = importName.substring(importName.lastIndexOf(".") + 1);
            if(!classNames.add(className)){
                addTypeError(program, "Duplicate class in imports: " + className);
                return null;
            }
        }
        return null;
    }

    private Void visitClass(JmmNode classes, SymbolTable table){
        Set<String> methods = new HashSet<>();
        for(JmmNode child : classes.getChildren()){
            if(child.getKind().equals("methodDecl")){
                String methodName = child.get("name");
                if(methods.contains(methodName)){
                    addTypeError(classes, "Duplicate method: " + methodName);
                    return null;
                }
                methods.add(methodName);
                visitMethodDecl(child, table);
            } else if(child.getKind().equals("varDecl") && !child.get("name").equals("main")){
                visitVarDecl(child, table);
            }
        }

        Set<String> fields = new HashSet<>();
        for(Symbol field : table.getFields()){
            if(!fields.add(field.getName())){
                addTypeError(classes, "Duplicate field: " + field.getName());
                return null;
            }
        }
        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        int returnCount = 0;
        for(JmmNode child : method.getChildren()){
            if(child.getKind().equals(Kind.RETURN_STMT.getNodeName())){
                returnCount++;
            }
        }

        if(returnCount > 1){
            addTypeError(method, "Multiple return statements in method");
            return null;
        }

        Set<String> parameters = new HashSet<>();
        for(Symbol parameter : table.getParameters(currentMethod)){
            if(!parameters.add(parameter.getName())){
                addTypeError(method, "Duplicate parameter: " + parameter.getName());
                return null;
            }
        }

        Set<String> locals = new HashSet<>();
        for(Symbol local : table.getLocalVariables(currentMethod)){
            if(!locals.add(local.getName())){
                addTypeError(method, "Duplicate local variable: " + local.getName());
                return null;
            }
        }

        List<Symbol> parametersList = table.getParameters(currentMethod);
        if(parametersList != null){
            boolean hasVararg = false;
            for(int i=0; i < parametersList.size(); i++){
                Symbol parameter = parametersList.get(i);
                if(parameter.getType().getName().equals("int...")){
                    if(i != parametersList.size() - 1){
                        addTypeError(method, "Vararg must be the last parameter");
                        return null;
                    }
                    if(hasVararg){
                        addTypeError(method, "Only one vararg parameter is allowed");
                        return null;
                    }
                    hasVararg = true;
                }
            }
        }

        var fields = table.getFields();

        if(Boolean.parseBoolean(method.get("isStatic"))){
            //if the current method has fields, an error should be reported
            if(!currentMethod.contains("main") && !fields.isEmpty()){
                addTypeError(method, "Static method cannot access fields");
                return null;
            }
        }


        if (!currentMethod.equals("main")) {
            Type returnType = table.getReturnType(currentMethod);
            if (returnType != null) {
                Optional<JmmNode> lastChildOpt = method.getChildrenStream().reduce((first, second) -> second);
                if (lastChildOpt.isPresent()) {
                    JmmNode lastChild = lastChildOpt.get();
                    Type lastChildType = TypeUtils.getExprType(lastChild, table);
                    if (!lastChildType.equals(returnType)) {
                        addTypeError(lastChild, "Return type is incorrect");
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        Type varType = TypeUtils.getVarDeclType(varDecl, table);
        if (varType.getName().equals(TypeUtils.getVarargTypeName())) {
            addTypeError(varDecl, "Vararg cannot be used in variable or field declaration");
        }
        return null;
    }

    private Void visitExpression(JmmNode expr, SymbolTable table){
        JmmNode expression = expr.getJmmChild(0);
        TypeUtils.getExprType(expression, table);

        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table) {
        Type arrayType = TypeUtils.getExprType(arrayInit, table);

        if (!TypeUtils.isArrayIntType(arrayType)) {
            addTypeError(arrayInit, "Array initializer can only be used in places that can accept an array of integers");
            return null;
        }

        List<JmmNode> elements = arrayInit.getChildren();
        if (!elements.isEmpty()) {
            Type elementType = TypeUtils.getExprType(elements.get(0), table);
            for (int i = 1; i < elements.size(); i++) {
                Type currentType = TypeUtils.getExprType(elements.get(i), table);
                if (!currentType.equals(elementType)) {
                    addTypeError(arrayInit, "All elements in the array should have the same type");
                    return null;
                }
            }
        }

        return null;
    }

    private Void visitLength(JmmNode lengthNode, SymbolTable table){
        JmmNode child = lengthNode.getJmmChild(0);
        Type leftChildType = TypeUtils.getExprType(child, table);

        if(!leftChildType.isArray()){
            addTypeError(lengthNode, "Method length only applies to arrays");
            return null;
        }

        return null;
    }

    private Void visitThis(JmmNode thisNode, SymbolTable table){
        JmmNode parent = thisNode.getParent();

        while(!parent.getKind().equals("Method")){
            parent = parent.getParent();
        }

        if(parent.getKind().equals("Method")){
            if(parent.get("name").equals("main")){
                addTypeError(thisNode, "Cannot use 'this' in static method");
                return null;
            }
        }

        return null;
    }


    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        Type leftType = TypeUtils.getExprType(binaryExpr.getChildren().get(0), table);
        Type rightType = TypeUtils.getExprType(binaryExpr.getChildren().get(1), table);
        String operator = binaryExpr.get("name");

        if(!leftType.getName().equals(rightType.getName())){
            if(leftType.getName().equals("any") || rightType.getName().equals("any")){
                return null;
            }else{
                addTypeError(binaryExpr, "Operands have different type");
                return null;
            }
        }else if(leftType.isArray() || rightType.isArray()){
            addTypeError(binaryExpr, "Cannot perform arithmetic operations on arrays");
            return null;
        }

        if (!isTypeCompatible(leftType, rightType, operator)) {
            addTypeError(binaryExpr, "Operands of operation '" + operator + "' are not compatible");
            return null;
        }

        return null;
    }

    private boolean isTypeCompatible(Type leftType, Type rightType, String operator) {
        return switch (operator) {
            case "+", "-", "*", "/", "<", ">", "<=", ">=", "==", "!=" -> leftType.getName().equals(TypeUtils.getIntTypeName()) &&
                    rightType.getName().equals(TypeUtils.getIntTypeName());
            case "&&", "||" -> leftType.getName().equals("boolean") &&
                    rightType.getName().equals("boolean");
            default -> false;
        };
    }

    private Void visitAccessArray(JmmNode accessArray, SymbolTable table){
        JmmNode left = accessArray.getJmmChild(0);
        JmmNode right = accessArray.getJmmChild(1);

        Type leftType = TypeUtils.getExprType(left, table);
        Type rightType = TypeUtils.getExprType(right, table);
        boolean isVararg = leftType.getName().equals("int...");

        if(!leftType.isArray() && !isVararg){
            addTypeError(accessArray, left.get("name") + " is not an array");
            return null;
        }
        if(!rightType.getName().equals("int")){
            addTypeError(accessArray, "Index is not an int");
            return null;
        }

        return null;
    }

    private Void visitArrayAssign(JmmNode assignArray, SymbolTable table){
        JmmNode childIndex = assignArray.getJmmChild(0);
        JmmNode childAssignment = assignArray.getJmmChild(1);

        Type indexType = TypeUtils.getExprType(childIndex, table);
        Type assignmentType = TypeUtils.getExprType(childAssignment, table);

        Type varType = TypeUtils.getExprType(assignArray, table);

        if(!varType.isArray()){
            addTypeError(assignArray, "Array assignment variable not array");
            return null;
        }

        if(!indexType.getName().equals("int")){
            addTypeError(assignArray, "Index needs to be an integer");
            return null;
        }
        if(!assignmentType.getName().equals("int")){
            addTypeError(assignArray, "Assignment needs to be an integer");
            return null;
        }

        return null;
    }

    private Void visitBrackets(JmmNode bracket, SymbolTable table){
        for (JmmNode child: bracket.getChildren()){
            if(child.getKind().equals("AssignStmt")){
                visitAssign(child, table);
            }
        }
        return null;
    }

    private Void visitAssign(JmmNode assign, SymbolTable table) {
        JmmNode leftNode = assign.getChildren().get(0);
        Type assigneeType = TypeUtils.getExprType(leftNode, table);

        JmmNode rightNode = assign.getChildren().get(1);
        Type assignedType = TypeUtils.getExprType(rightNode, table);

        if(!leftNode.getKind().equals("VarRefExpr") && !leftNode.getKind().equals("AccessArray")){
            addTypeError(assign, "Left side of assignment must be an ID");
            return null;
        }

        if(assignedType == null){
            addTypeError(assign, "Class not imported");
            return null;
        }

        if(assigneeType == null){
            addTypeError(assign, "Field cannot be used in static class");
            return null;
        }

        if(assigneeType.getName().equals(table.getSuper()) && assignedType.getName().equals(table.getClassName())){
            return null;
        } else if(table.getImports().contains(assigneeType.getName()) && table.getImports().contains(assignedType.getName())){
            return null;
        }

        if(!TypeUtils.areTypesAssignable(assigneeType, assignedType)){
            addTypeError(assign, "Type of the assignee " + assigneeType + " is not compatible with the assigned " + assignedType);
            return null;
        }

        return null;
    }


    private Void visitReturn(JmmNode returns, SymbolTable table){
        JmmNode expression = returns.getJmmChild(0);
        Type exprType = TypeUtils.getExprType(expression, table);
        Type expectedType = table.getReturnType(currentMethod);
        if(!TypeUtils.areTypesAssignable(exprType, expectedType)){
            addTypeError(returns, "Return type is incorrect");
            return null;
        }
        return null;
    }

    private Void visitIf(JmmNode ifNode, SymbolTable table) {
        Type conditionType = TypeUtils.getExprType(ifNode.getChildren().get(0), table);
        if (!conditionType.getName().equals("boolean")) {
            addTypeError(ifNode, "Expression in condition does not return a boolean");
            return null;
        }

        return null;
    }

    private Void visitWhile(JmmNode whileNode, SymbolTable table) {
        JmmNode child = whileNode.getJmmChild(0);
        Type conditionType = TypeUtils.getExprType(child, table);

        if (!conditionType.getName().equals("boolean")) {
            addTypeError(whileNode, "Expression in condition does not return a boolean");
            return null;
        }

        return null;
    }

    private Void visitNot(JmmNode not, SymbolTable table){
        JmmNode child = not.getJmmChild(0);
        Type childType = TypeUtils.getExprType(child, table);

        if(!childType.getName().equals("boolean")){
            addTypeError(not, "Not operator only allowed for boolean type");
            return null;
        }
        return null;
    }

    private Void visitParenthesis(JmmNode parenthesis, SymbolTable table){
        TypeUtils.getExprType(parenthesis, table);
        return null;
    }


    private Void visitFunctionCall(JmmNode functionCall, SymbolTable table) {
        String methodName = functionCall.get("name");
        JmmNode classCall = functionCall.getJmmChild(0);
        var parent = functionCall.getAncestor(Kind.METHOD_DECL).get();
        var locals = table.getLocalVariables(parent.get("name")).stream().map(Symbol::getName).toList();
        var fields = table.getFields().stream().map(Symbol::getName).toList();
        var imports = table.getImports().stream().map(imp -> imp.substring(imp.lastIndexOf(".") + 1)).toList();

        if(!classCall.get("name").equals("this") && !locals.contains(classCall.get("name")) && !fields.contains(classCall.get("name"))){
            if(!imports.contains(classCall.get("name"))) {
                addTypeError(functionCall, "The variable" + classCall + "doesn't exist");
                return null;
            }
        }

        var methods = table.getMethods();
        var classCallType = TypeUtils.getExprType(classCall, table);

        if(!methods.contains(methodName)){
            if (table.getSuper().isEmpty()) {
                if(!(imports.contains(classCallType.getName())) && !imports.contains(classCall.get("name"))) {
                    addTypeError(functionCall, "Method doesn't exist");
                    return null;
                }
            }
        }

        var parameters = table.getParameters(methodName);
        if(parameters.size() != (functionCall.getNumChildren() - 1) && !imports.contains(classCallType.getName())){
            boolean hasVarargs = parameters.stream().anyMatch(param -> param.getType().getName().equals("int..."));
            if(!hasVarargs){
                addTypeError(functionCall, "Wrong number of arguments");
            } else{
                var args = functionCall.getChildren().subList(1, functionCall.getChildren().size());

                String firstArgKind = args.get(0).getKind();
                boolean allSameKind = args.stream().allMatch(arg -> arg.getKind().equals(firstArgKind));

                if(!allSameKind){
                    addTypeError(functionCall, "All varargs arguments must be of the same type");
                    return null;
                }
            }
        }
        for(Symbol parameter : parameters){
            if(!parameter.getName().equals("main") && !imports.contains(classCallType.getName())) {
                if (parameters.isEmpty()) return null;
                var paramTypes = parameters.stream().map(Symbol::getType).toList();
                var args = functionCall.getChildren().subList(1, functionCall.getChildren().size());
                var argTypes = args.stream().map(arg -> TypeUtils.getExprType(arg, table)).toList();
                int j = 0;
                for (int i = 0; i < args.size(); i++) {
                    if (!TypeUtils.areTypesAssignable(argTypes.get(i), paramTypes.get(j))) {
                        addTypeError(functionCall, "Method received was not the method expected");
                        return null;
                    }
                    if (paramTypes.get(j).getName().equals("int...") && args.size() - i > 1) continue;
                    j++;
                }
            }
        }
        return null;
    }

    private void addTypeError(JmmNode node, String message) {
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );
    }
}
