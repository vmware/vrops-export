grammar opsql;

query
    : Select fieldList From resourceSpecifier (Where filterSpecifier)
    ;

fieldList
    : field
    | fieldList ',' field
    ;

field
    : Identifier
    ;

resourceSpecifier
    : Identifier
    | Identifier ':' Identifier
    ;

filterSpecifier
    : andExpression
    | orExpression
    ;

andExpression:
      andExpression (And andExpression)
    | booleanExpression
    ;

orExpression:
      orExpression (And orExpression)
    | booleanExpression
    ;

booleanExpression
    : simpleBooleanExpression
    | Not simpleBooleanExpression
    ;

simpleBooleanExpression
    : Identifier BooleanOperator Literal
   ;

/// Reserved words
Select:     'select' 'hello';
From:       'from';
Where:      'where';
And:        'and';
Or:         'or';
Not:        'not';
Parent:     'parent';
Child:      'child';
Prop:       '$prop';

BooleanOperator

    : '='
    | '!='
    | '>'
    | '<'
    | '>='
    | '<='
    | 'contains'
    ;

Literal:
      StringLiteral
    | Number
    ;

Identifier:
    ValidIdStart ValidIdChar*
    ;

fragment EscapeSequence
    :   SimpleEscapeSequence
    |   OctalEscapeSequence
    |   HexadecimalEscapeSequence
    ;

fragment SimpleEscapeSequence
    :   '\\' ['"?abfnrtv\\]
    ;

fragment OctalEscapeSequence
    :   '\\' OctalDigit OctalDigit? OctalDigit?
    ;
fragment HexadecimalEscapeSequence
    :   '\\x' HexadecimalDigit+
    ;

fragment
OctalDigit
    :   [0-7]
    ;

fragment
HexadecimalDigit
    :   [0-9a-fA-F]
    ;

StringLiteral
    :  '"' SCharSequence? '"'
    ;

fragment SCharSequence
    :   SChar+
    ;

fragment SChar
    :   ~["\\\r\n]
    |   EscapeSequence
    |   '\\\n'   // Added line
    |   '\\\r\n' // Added line
    ;

fragment ValidIdStart
   : ('a' .. 'z') | ('A' .. 'Z') | '_'
   ;

fragment ValidIdChar
   : ValidIdStart | ('0' .. '9')
   ;

WS
   : [ \r\n\t] + -> skip
   ;

ScientificNumber
   : Sign? Number ((E1 | E2) Sign? Number)?
   ;


fragment Number
   : ('0' .. '9') + ('.' ('0' .. '9') +)?
   ;

fragment E1
   : 'E'
   ;


fragment E2
   : 'e'
   ;

fragment Sign
   : ('+' | '-')
   ;