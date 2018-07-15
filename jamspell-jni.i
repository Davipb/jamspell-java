%module JamSpell

%rename(NativeSpellCorrector) TSpellCorrector;
%rename("%(lowercamelcase)s", %$isfunction) "";

%ignore GetCandidatesRaw;

SWIG_JAVABODY_PROXY(public, public, SWIGTYPE)
SWIG_JAVABODY_TYPEWRAPPER(public, public, public, SWIGTYPE)

%typemap(javafinalize) SWIGTYPE %{%}
%typemap(javaclassmodifiers) SWIGTYPE %{public final class%}

%include "JamSpell/jamspell.i"
