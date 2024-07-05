package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String VARARGS_NAME = "int...";
    private static String currentMethod;
    private static final String ANYTIPE_NAME = "any";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static String getVarargTypeName() {
        return VARARGS_NAME;
    }

    public static String getAnyType(){
        return ANYTIPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR, BINARY_OP -> getBinExprType(expr, table);
            case VAR_REF_EXPR, LITERAL, ASSIGN_ARRAY, BRACKETS, PARAM, PARENTHESIS, TYPE, ASSIGN_STMT, EXPR_STMT, RETURN_STMT-> getVarExprType(expr, table);
            case INTEGER_LITERAL, INTEGER, ACCESS_ARRAY, LENGTH -> new Type(INT_TYPE_NAME, false);
            case BOOL, IF, NOT -> new Type("boolean", false);
            case ARRAY_DECL, ARRAY_INIT -> new Type(INT_TYPE_NAME, true);
            case THIS -> new Type(table.getClassName(), false);
            case VAR_DECLARATION, VAR_DECL -> getVarDeclType(expr, table);
            case NEW -> {
                var className = expr.get("name");
                yield new Type(className, false);
            }
            case VARARG -> new Type(VARARGS_NAME, true);
            case FUNCTION_CALL ->  getFunctionCallType(expr, table);

            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }


    public static Type getVarDeclType(JmmNode varDecl, SymbolTable table) {
        return new Type(varDecl.get("name"), false);
    }


    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
        String operator = binaryExpr.get("name");

        Type leftType = getExprType(binaryExpr.getChildren().get(0), table);

        return switch (operator) {
            case "+", "-", "*", "/" -> new Type(INT_TYPE_NAME, false);
            case "<", ">", "<=", ">=", "==", "!=", "&&", "||" -> new Type("boolean", false);
            default -> new Type(leftType.getName(), leftType.isArray());
        };

    }

    public static boolean isArrayIntType(Type type) {
        return type.getName().equals("int") && type.isArray();
    }

    public static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        if (varRefExpr.getKind().equals("Parenthesis")) {
            return getExprType(varRefExpr.getJmmChild(0), table);
        }

        String varName = varRefExpr.get("name");

        var parent = varRefExpr.getAncestor(Kind.METHOD_DECL);


        if(parent.isPresent()){
            var call = parent.get();
            String methodName = call.get("name");
            List<Symbol> locals = table.getLocalVariables(methodName);
            if(locals != null){
                for(Symbol local : locals){
                    if(local.getName().equals(varName)){
                        return local.getType();
                    }
                }
            }

            List<Symbol> parameters = table.getParameters(methodName);
            if(parameters != null){
                for(Symbol parameter : parameters){
                    if(parameter.getName().equals(varName)){
                        return parameter.getType();
                    }
                }
            }

            List<Symbol> fields = table.getFields();
            if(fields != null){
                for(Symbol field : fields){
                    if(field.getName().equals(varName)){
                        if(methodName.equals("main")){
                            return null;
                        }
                        return field.getType();
                    }
                }
            }

            var imports = table.getImports().stream().map(imp -> imp.substring(imp.lastIndexOf(".") + 1)).toList();
            if(imports.contains(varName)){
                return new Type(varName, false);
            }

            if (varName.equals("true") || varName.equals("false")) {
                return new Type("boolean", false);
            }


        }

        return new Type("undefined", false);
    }

    private static Type getFunctionCallType(JmmNode functionCall, SymbolTable table){
        var function = functionCall.getChild(0);
        var kind = Kind.fromString(function.getKind());

        if(kind.equals(Kind.THIS)){
            var functionType = table.getReturnType(functionCall.get("name"));
            return functionType;
        }

        var imports = table.getImports();
        if(!imports.isEmpty()){
            imports = imports.stream().map(imp -> imp.substring(imp.lastIndexOf(".") + 1)).toList();
            var type = TypeUtils.getExprType(function, table);
            if(imports.contains(type.getName())){
                return new Type(ANYTIPE_NAME, false);
            }
        }

        return table.getReturnType(functionCall.get("name"));
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        String sourceName = sourceType.getName();
        String destinationName = destinationType.getName();

        if((sourceName.equals(INT_TYPE_NAME) && destinationName.equals(VARARGS_NAME)) || destinationName.equals(ANYTIPE_NAME)){
            return true;
        }
            return (sourceName.equals(destinationName)) && (sourceType.isArray() == destinationType.isArray());
    }
}
