grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRAC : '[' ;
RBRAC : ']' ;
DOTS : '...' ;
DOT : '.' ;
COMMA : ',' ;

AND : '&&' ;
LESS : '<' ;
MRE : '>' ;
MUL : '*' ;
ADD : '+' ;
MINUS : '-' ;
DIV : '/' ;
BOOL : 'true' | 'false' ;
NOT : '!' ;

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
STRING : 'String' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
BOOLEAN : 'boolean' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
NEW : 'new' ;
THIS : 'this' ;
LENGTH : 'length' ;

INTEGER : [0] | [1-9][0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;
SINGLE_LINE_COMMENT: '//' ~[\n]* -> skip;
MULTI_LINE_COMMENT: '/*' .*? '*/' -> skip;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDecl EOF
    ;

importDeclaration
        : IMPORT name+=(ID | LENGTH | THIS | MAIN) (DOT name+=(ID | LENGTH | THIS | MAIN))* SEMI;


classDecl
    : CLASS className=ID
        (EXTENDS superclassName=ID)?
        LCURLY
        (varDecl)*
        (methodDecl)*
        RCURLY
    ;

varDecl
    : type name=(ID | LENGTH | THIS | MAIN) SEMI
    | type name=MAIN SEMI
    ;

type
    locals[boolean isArray = false, boolean isVarargs = false]
    : name= INT LBRAC RBRAC {$isArray = true;} #Array
    | name= INT DOTS  {$isVarargs = true;} #Vararg
    | name= BOOLEAN #Boolean
    | name= INT #Integer
    | name= ID #Literal
    | name= STRING #String
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})?
        (STATIC {$isStatic=true;})?
        type name=(ID | LENGTH | THIS | MAIN)
        LPAREN (param (COMMA param)*)? RPAREN
        LCURLY (varDecl)* (stmt)* RETURN expr SEMI RCURLY #Method
    | (PUBLIC {$isPublic=true; $isStatic=true;})?
        STATIC VOID name=MAIN LPAREN STRING LBRAC RBRAC (ID | LENGTH | THIS | MAIN) RPAREN
        LCURLY (varDecl)* (stmt)* RCURLY #Method
    ;

param
    : type name=(ID | LENGTH | THIS)
    ;

returnType
    : type
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt
    | RETURN expr SEMI #ReturnStmt
    | LCURLY (stmt)* RCURLY  #Brackets
    | IF LPAREN expr RPAREN stmt (ELSE stmt) #If
    | WHILE LPAREN expr RPAREN stmt #While
    | expr SEMI #ExprStmt
    | name=(ID | LENGTH | THIS | MAIN) EQUALS expr SEMI #Assign
    | name=(ID | LENGTH | THIS | MAIN) LBRAC expr RBRAC EQUALS expr SEMI #AssignArray
    ;

expr
    : LPAREN expr RPAREN #Parenthesis
    | NEW INT LBRAC expr RBRAC #ArrayDecl
    | expr DOT name=(ID | LENGTH | THIS | MAIN) LPAREN (expr(COMMA expr)*)? RPAREN #FunctionCall
    | expr LBRAC expr RBRAC #AccessArray
    | NEW name=(ID | LENGTH | THIS | MAIN) LPAREN RPAREN #VarDeclaration
    | expr DOT LENGTH #Length
    | name=THIS #This
    | NOT expr #Not
    | expr name=(MUL | DIV) expr #BinaryOp
    | expr name=(ADD | MINUS) expr #BinaryOp
    | expr name= AND expr #BinaryOp
    | expr name=(LESS | MRE) expr #BinaryOp  //MORE é uma palavra reservada
    | name=INTEGER #IntegerLiteral
    | name=BOOL #VarRefExpr
    | name=(ID | LENGTH | THIS | MAIN) #VarRefExpr
    | LBRAC expr (COMMA expr)*? RBRAC #ArrayInit   //The array can be an empty list or contain 1 or more expressions - [10,20,30]
    ;