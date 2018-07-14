%module Jamspell

%rename(NativeSpellCorrector) TSpellCorrector;
%rename("%(lowercamelcase)s", %$isfunction) "";

%ignore GetCandidatesRaw;

%include "JamSpell/jamspell.i"
