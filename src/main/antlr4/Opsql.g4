grammar Opsql;

@header {
    package com.vmware.vropsexport.opsql;
}

query
    : Resource '(' resource=Identifier ')' ('.' filter)* '.' Fields '(' fieldList ')'   # queryStatement
    | Set Identifier '=' literal                                                        # setStatement
    ;

fieldList
    : fieldSpecifier (',' fieldSpecifier)*
    ;

fieldSpecifier
    : propertyOrMetricIdentifier                                 # simpleField
    | field=propertyOrMetricIdentifier As? alias=Identifier      # aliasedField
    ;

filter
    : WhereMetrics '(' expr=booleanExpression ')'               # whereMetrics
    | WhereProperties '(' expr=booleanExpression ')'            # whereProperties
    | WhereHealth '(' args=stringLiteralList ')'                # whereHealth
    | WhereTags '(' args=stringLiteralList ')'                  # whereTags
    | WhereState '(' args=stringLiteralList ')'                 # whereState
    | WhereStatus '(' args=stringLiteralList ')'                # whereStatus
    | Name '(' args=stringLiteralList ')'                       # whereName
    | RegEx '(' args=stringLiteralList ')'                      # whereRegex
    ;


booleanExpression
    : comparison (And comparison)+                              # andExpression
    | comparison (Or comparison)+                               # orExpression
    | comparison                                                # simpleExpression
    ;

comparison
    : Not propertyOrMetricIdentifier op=BooleanOperator literal # negatedComparison
    | propertyOrMetricIdentifier BooleanOperator literal        # normalComparison
    ;

stringLiteralList
    : StringLiteral (',' StringLiteral)*
    ;

literal
    : StringLiteral                                             # stringLiteral
    | ScientificNumber                                          # number
    ;

propertyOrMetricIdentifier
    : PropertyIdentifier                                        # propertyIdentifier
    | Identifier                                                # metricIdentifier
    ;

/// Reserved words
Resource:           'resource';
WhereMetrics:       'whereMetrics';
WhereProperties:    'whereProperties';
WhereHealth:        'whereHealth';
WhereState:         'whereState';
WhereStatus:        'whereStatus';
WhereTags:          'whereTags';
Fields:             'fields';
RegEx:              'regex';


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
As:         'as';
Metrics:    'metrics';
Properties: 'properties';
Set:        'set';

BooleanOperator
    : '='
    | '!='
    | '>'
    | '<'
    | '>='
    | '<='
    | 'contains'
    | 'in'
    ;

PropertyIdentifier
    : '@' Identifier
    ;

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

WS
   : [ \r\n\t] + -> skip
   ;