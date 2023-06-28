grammar Opsql;

@header {
    package com.vmware.vropsexport.opsql;
}

query
    : Resource '(' resource=Identifier ')'
        parentsDeclaration?
        childrenDeclaration?
        ('.' filter)*
        '.' Fields '(' fieldList ')'                            # queryStatement
    | Set Identifier '=' literal                                # setStatement
    ;

fieldList
    : fieldSpecifier (',' fieldSpecifier)*
    ;

fieldSpecifier
    : propertyOrMetricIdentifier                                 # simpleField
    | field=propertyOrMetricIdentifier As? alias=Identifier      # aliasedField
    ;

parentsDeclaration
    : ('.' Parents '(' relatives=relationshipList ')' )
    ;

childrenDeclaration
    : ('.' Children '(' relatives=relationshipList ')' )
    ;

relationshipList
    : relationshipSpecifier (',' relationshipSpecifier)*
    ;

relationshipSpecifier
    : resourceType=Identifier ('(' depth=PositiveInteger ')')? As? alias=Identifier
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
    : Not propertyOrMetricIdentifier op=booleanOperator literal # negatedComparison
    | propertyOrMetricIdentifier booleanOperator literal        # normalComparison
    ;

stringLiteralList
    : StringLiteral (',' StringLiteral)*
    ;

literal
    : StringLiteral                                             # stringLiteral
    | ScientificNumber                                          # number
    ;

propertyOrMetricIdentifier
    : resource=Identifier '->' field=PropertyIdentifier         # relativePropertyIdenfifier
    | aggregation '(' resource=Identifier '->' field=Identifier ')' # relativeMetricIdentifier
    | PropertyIdentifier                                        # propertyIdentifier
    | Identifier                                                # metricIdentifier
    ;

booleanOperator
    : '!='
    | '>'
    | '<'
    | '>='
    | '<='
    | '='
    | 'contains'
    | 'in'
    ;

aggregation
    : Avg
    | Sum
    | Min
    | Max
    | StdDev
    | Variance
    | First
    | Last
    | Median
    ;

/// Reserved words
And:                'and';
As:                 'as';
Avg:                'avg';
Child:              'child';
Children:           'children';
Fields:             'fields';
First:              'first';
From:               'from';
Health:             'health';
Id:                 'id';
Last:               'last';
Max:                'max';
Median:             'median';
Metrics:            'metrics';
Min:                'min';
Name:               'name';
Not:                'not';
Or:                 'or';
Parent:             'parent';
Parents:            'parents';
Properties:         'properties';
RegEx:              'regex';
Resource:           'resource';
Select:             'select';
Set:                'set';
State:              'state';
Status:             'status';
StdDev:             'stddev';
Sum:                'sum';
Tag:                'tag';
Variance:           'variance';
Where:              'where';
WhereHealth:        'whereHealth';
WhereMetrics:       'whereMetrics';
WhereProperties:    'whereProperties';
WhereState:         'whereState';
WhereStatus:        'whereStatus';
WhereTags:          'whereTags';

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
    : '|' | ':' | '$' | '.'
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

PositiveInteger
    : ('0' .. '9')+
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