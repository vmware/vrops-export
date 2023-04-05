grammar Opsql;

@header {
    package com.vmware.vropsexport.opsql;
}

query
    : Select fieldList From resourceSpecifier (Where filterExpression)? EOF
    ;

fieldList
    : Identifier
    | fieldList ',' Identifier
    ;

resourceSpecifier
    : Identifier
    ;

filterExpression
    : Not filterExpression                      # negation
    | booleanTerm BooleanOperator literal       # comparison
    | filterExpression And filterExpression     # andExpression
    | filterExpression Or filterExpression      # orExpression
    | '(' filterExpression ')'                  # subExpression
    ;

booleanTerm
    : Identifier                                # identifierTerm
    | reservedFieldName                         # specialTerm
    ;

reservedFieldName
    : Name
    | Id
    | Tag
    ;

literal
    : StringLiteral                             # stringLiteral
    | ScientificNumber                          # number
    ;

/// Reserved words
Select:     'select';
From:       'from';
Where:      'where';
And:        'and';
Or:         'or';
Not:        'not';
Parent:     'parent';
Child:      'child';
Name:       'name';
Id:         'id';
Tag:        'tag';
Health:     'health';
Status:     'status';
State:      'state';

BooleanOperator
    : '='
    | '!='
    | '>'
    | '<'
    | '>='
    | '<='
    | 'contains'
    ;

// Identifiers
Identifier
    : ValidIdStart ValidIdChar*
    ;

 fragment ValidIdStart
    : ('a' .. 'z') | ('A' .. 'Z') | '_'
    ;

 fragment ValidIdChar
    : ValidIdStart | ('0' .. '9') | SpecialVropIdentifierChars
    ;

 fragment SpecialVropIdentifierChars
    : [|$:]
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
//    | '\'' SCharSequence? '\''
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