package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmSymbolTable implements SymbolTable {

    private final String className;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;

    private final List<String> imports;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final String superclassName;

    private final List<Symbol> fields;
    private final List<String> staticMethods;

    public JmmSymbolTable(String className,
                          String superclassName,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          List<String> imports,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          List<Symbol> fields,
                          List<String> staticMethods)
                            {
        this.className = className;
        this.superclassName = superclassName;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.imports = imports;
        this.params = params;
        this.locals = locals;
        this.fields = fields;
        this.staticMethods = staticMethods;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superclassName;
    }

    @Override
    public List<Symbol> getFields() {
        return Collections.unmodifiableList(fields);
    }

    @Override
    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public Type getReturnType(String methodSignature) {
        Type returnType = returnTypes.get(methodSignature);
        return returnType;
    }


    @Override
    public List<Symbol> getParameters(String methodSignature) {
        List<Symbol> parameters = params.get(methodSignature);
        return parameters != null ? Collections.unmodifiableList(parameters) : Collections.emptyList();
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        List<Symbol> localVariables = locals.get(methodSignature);
        return localVariables != null ? Collections.unmodifiableList(localVariables) : Collections.emptyList();
    }

    public List<String> getStaticMethods() {
        return Collections.unmodifiableList(staticMethods);
    }

}
