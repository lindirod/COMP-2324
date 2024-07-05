package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        List<String> staticMethods = new ArrayList<>();

        var classDecl = root.getChildren(CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("className");
        String superclassName;

        if (classDecl.hasAttribute("superclassName")) {
            superclassName = classDecl.get("superclassName");
        } else {
            superclassName = "";
        }

        var importDeclaration = buildImports(root);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, superclassName, methods, returnTypes, importDeclaration, params, locals, fields, staticMethods);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).forEach(method -> {
                    String tipo;
                    boolean isArray = false;

                    if (method.get("name").equals("main")) {
                        tipo = "void";
                    } else {
                        tipo = method.getJmmChild(0).get("name");
                        if(Boolean.parseBoolean(method.getJmmChild(0).get("isArray"))){
                            isArray = true;
                        }
                    }

                    map.put(method.get("name"), new Type(tipo, isArray));
                });
        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            String methodName = method.get("name");

            List<Symbol> parameters = method.getChildren(PARAM).stream()
                    .map(parameterNode -> {
                        String type = parameterNode.getJmmChild(0).get("name");
                        boolean isArray = Boolean.parseBoolean(parameterNode.getJmmChild(0).get("isArray"));
                        boolean isVarargs = Boolean.parseBoolean(parameterNode.getJmmChild(0).get("isVarargs"));
                        if(isVarargs){
                            return new Symbol(new Type ("int...", isArray), parameterNode.get("name"));
                        }

                        return new Symbol(new Type (type, isArray), parameterNode.get("name"));
                    })
                    .collect(Collectors.toList());

            map.put(methodName, parameters);
        });

        return map;
    }


    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            String methodName = method.get("name");
            List<Symbol> locals = getLocalsList(method);
            map.put(methodName, locals);
        });

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();
        List<String> staticMethods = new ArrayList<>();

        for(var child : classDecl.getChildren(METHOD_DECL)){
            methods.add(child.get("name"));
            if(Boolean.parseBoolean(child.get("isStatic"))){
                staticMethods.add(child.get("name"));
            }
        }
        return methods;
    }

    private static List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();

        root.getChildren(IMPORT_DECLARATION).stream().forEach(decl ->{
            List<Object> namesObj = decl.getObjectAsList("name");
            List<String> names = namesObj.stream().map(Object::toString).toList();
            String importClasses = String.join(".", names);
            imports.add(importClasses);
                }

        );
        return imports;
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        List<Symbol> locals = new ArrayList<>();

        methodDecl.getChildren(VAR_DECL).forEach(varDecl -> {
            String varName = varDecl.get("name");
            String type = varDecl.getJmmChild(0).get("name");
            boolean isArray = Boolean.parseBoolean(varDecl.getJmmChild(0).get("isArray"));

            Type intType = new Type(type, isArray);
            Symbol symbol = new Symbol(intType, varName);

            locals.add(symbol);
        });

        return locals;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {

        List<Symbol> fields = new ArrayList<>();
        classDecl.getChildren(VAR_DECL).forEach(field -> {
            String fieldName = field.get("name");
            String type = field.getJmmChild(0).get("name");

            boolean isArray = Boolean.parseBoolean(field.getJmmChild(0).get("isArray"));

            Type aux = new Type(type, isArray);
            Symbol rers = new Symbol(aux,fieldName);
            fields.add(rers);
        });

        return fields;

    }


}
