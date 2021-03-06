/*
 * Sonar Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.resolve;

import com.google.common.annotations.VisibleForTesting;

/**
 * Routines for name resolution.
 * <p/>
 * Lookup by name and then filter by type is performant, because amount of symbols with same name are relatively small.
 * <p/>
 * Naming conventions:
 * env - is the environment where the symbol was mentioned
 * site - is the type of which symbol is a member
 * name - is the symbol's name
 * <p/>
 * TODO site should be represented by class Type
 */
public class Resolve {

  private final SymbolNotFound symbolNotFound = new SymbolNotFound();

  static class Env {
    /**
     * The next enclosing environment.
     */
    Env next;

    /**
     * The environment enclosing the current class.
     */
    Env outer;

    Symbol.PackageSymbol packge;

    Symbol.TypeSymbol enclosingClass;

    Scope scope;

    Env outer() {
      return outer;
    }

    Symbol.TypeSymbol enclosingClass() {
      return enclosingClass;
    }

    public Symbol.PackageSymbol packge() {
      return packge;
    }

    Scope scope() {
      return scope;
    }

    public Env dup() {
      Env env = new Env();
      env.next = this;
      env.outer = this.outer;
      env.packge = this.packge;
      env.enclosingClass = this.enclosingClass;
      env.scope = this.scope;
      return env;
    }
  }

  /**
   * Finds field with given name.
   */
  private Symbol findField(Env env, Symbol.TypeSymbol site, String name, Symbol.TypeSymbol type) {
    Symbol bestSoFar = symbolNotFound;
    for (Symbol symbol : type.members().lookup(name)) {
      if (symbol.kind == Symbol.VAR) {
        return isAccessible(env, site, symbol)
          ? symbol
          : new AccessErrorSymbol(symbol);
      }
    }
    Symbol symbol;
    if (type.getSuperclass() != null) {
      symbol = findField(env, site, name, type.getSuperclass());
      if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    for (Symbol.TypeSymbol interfaceType : type.getInterfaces()) {
      symbol = findField(env, site, name, interfaceType);
      if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    return bestSoFar;
  }

  /**
   * Finds variable or field with given name.
   */
  private Symbol findVar(Env env, String name) {
    Symbol bestSoFar = symbolNotFound;

    Env env1 = env;
    while (env1.outer() != null) {
      Symbol sym = null;
      for (Symbol symbol : env1.scope().lookup(name)) {
        if (symbol.kind == Symbol.VAR) {
          sym = symbol;
        }
      }
      if (sym == null) {
        sym = findField(env1, env1.enclosingClass(), name, env1.enclosingClass());
      }
      if (sym.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return sym;
      } else if (sym.kind < bestSoFar.kind) {
        bestSoFar = sym;
      }
      env1 = env1.outer();
    }

    // TODO imports

    return bestSoFar;
  }

  public Symbol findMemberType(Env env, Symbol.TypeSymbol site, String name, Symbol.TypeSymbol c) {
    Symbol bestSoFar = symbolNotFound;
    for (Symbol symbol : c.members().lookup(name)) {
      if (symbol.kind == Symbol.TYP) {
        return isAccessible(env, site, symbol)
          ? symbol
          : new AccessErrorSymbol(symbol);
      }
    }
    if (c.getSuperclass() != null) {
      Symbol symbol = findMemberType(env, site, name, c.getSuperclass());
      if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    for (Symbol.TypeSymbol interfaceType : c.getInterfaces()) {
      Symbol symbol = findMemberType(env, site, name, interfaceType);
      if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    return bestSoFar;
  }

  /**
   * Finds type with given name.
   */
  private Symbol findType(Env env, String name) {
    Symbol bestSoFar = symbolNotFound;

    for (Env env1 = env; env1.outer() != null; env1 = env1.outer()) {
      for (Symbol symbol : env1.scope().lookup(name)) {
        if (symbol.kind == Symbol.TYP) {
          return symbol;
        }
      }
      Symbol symbol = findMemberType(env1, env1.enclosingClass(), name, env1.enclosingClass());
      if (symbol.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return symbol;
      } else if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }

    // TODO imports
    for (Symbol symbol : env.packge().members.lookup(name)) {
      if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }

    return bestSoFar;
  }

  /**
   * @param kind subset of {@link Symbol#VAR}, {@link Symbol#TYP}, {@link Symbol#PCK}
   */
  public Symbol findIdent(Env env, String name, int kind) {
    Symbol bestSoFar = symbolNotFound;
    Symbol symbol;
    if ((kind & Symbol.VAR) != 0) {
      symbol = findVar(env, name);
      if (symbol.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return symbol;
      } else if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    if ((kind & Symbol.TYP) != 0) {
      symbol = findType(env, name);
      if (symbol.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return symbol;
      } else if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    if ((kind & Symbol.PCK) != 0) {
      // TODO read package
    }
    return bestSoFar;
  }

  /**
   * @param kind subset of {@link Symbol#TYP}, {@link Symbol#PCK}
   */
  public Symbol findIdentInPackage(Env env, Symbol site, String name, int kind) {
    // TODO implement me
    return symbolNotFound;
  }

  /**
   * @param kind subset of {@link Symbol#VAR}, {@link Symbol#TYP}
   */
  public Symbol findIdentInType(Env env, Symbol.TypeSymbol site, String name, int kind) {
    Symbol bestSoFar = symbolNotFound;
    Symbol symbol;
    if ((kind & Symbol.VAR) != 0) {
      symbol = findField(env, site, name, site);
      if (symbol.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return symbol;
      } else if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    if ((kind & Symbol.TYP) != 0) {
      symbol = findMemberType(env, site, name, site);
      if (symbol.kind < Symbol.ERRONEOUS) {
        // symbol exists
        return symbol;
      } else if (symbol.kind < bestSoFar.kind) {
        bestSoFar = symbol;
      }
    }
    return bestSoFar;
  }

  /**
   * Finds method with given name.
   */
  public Symbol findMethod(Env env, Symbol.TypeSymbol site, String name) {
    // TODO correct implementation will require types of arguments, ...
    Symbol bestSoFar = symbolNotFound;
    for (Symbol symbol : site.enclosingClass().members().lookup(name)) {
      if ((symbol.kind == Symbol.MTH) && isAccessible(env, site, symbol)) {
        if (bestSoFar.kind < Symbol.ERRONEOUS) {
          return new AmbiguityErrorSymbol();
        }
        bestSoFar = symbol;
      }
    }
    return bestSoFar;
  }

  /**
   * Is class accessible in given environment?
   */
  public boolean isAccessible(Env env, Symbol.TypeSymbol c) {
    final boolean result;
    switch (c.flags() & Flags.ACCESS_FLAGS) {
      case Flags.PRIVATE:
        result = (env.enclosingClass().outermostClass() == c.owner().outermostClass());
        break;
      case 0:
        result = (env.packge() == c.packge());
        break;
      case Flags.PUBLIC:
        result = true;
        break;
      case Flags.PROTECTED:
        result = (env.packge() == c.packge()) || isInnerSubClass(env.enclosingClass(), c.owner());
        break;
      default:
        throw new IllegalStateException();
    }
    // TODO check accessibility of enclosing type: isAccessible(env, c.type.getEnclosingType())
    return result;
  }

  /**
   * Is given class a subclass of given base class, or an inner class of a subclass?
   */
  private boolean isInnerSubClass(Symbol.TypeSymbol c, Symbol base) {
    while (c != null && isSubClass(c, base)) {
      c = c.owner().enclosingClass();
    }
    return c != null;
  }

  /**
   * Is given class a subclass of given base class?
   */
  @VisibleForTesting
  boolean isSubClass(Symbol.TypeSymbol c, Symbol base) {
    // TODO get rid of null check
    if (c == null) {
      return false;
    }
    // TODO see Javac
    if (c == base) {
      // same class
      return true;
    } else if ((base.flags() & Flags.INTERFACE) != 0) {
      // check if class implements base
      for (Symbol.TypeSymbol interfaceSymbol : c.getInterfaces()) {
        if (isSubClass(interfaceSymbol, base)) {
          return true;
        }
      }
      // check if superclass implements base
      return isSubClass(c.getSuperclass(), base);
    } else {
      // check if class extends base or its superclass extends base
      return isSubClass(c.getSuperclass(), base);
    }
  }

  /**
   * Is symbol accessible as a member of given class in given environment?
   * <p/>
   * Symbol is accessible only if not overridden by another symbol. If overridden, then strictly speaking it is not a member.
   */
  public boolean isAccessible(Env env, Symbol.TypeSymbol site, Symbol symbol) {
    switch (symbol.flags() & Flags.ACCESS_FLAGS) {
      case Flags.PRIVATE:
        // no check of overriding, because private members cannot be overridden
        return (env.enclosingClass().outermostClass() == symbol.owner().outermostClass())
          && isInheritedIn(symbol, site);
      case 0:
        return (env.packge() == symbol.packge())
          && isAccessible(env, site)
          && isInheritedIn(symbol, site)
          && notOverriddenIn(site, symbol);
      case Flags.PUBLIC:
        return isAccessible(env, site)
          && notOverriddenIn(site, symbol);
      case Flags.PROTECTED:
        return ((env.packge() == symbol.packge()) || isProtectedAccessible(symbol, env.enclosingClass, site))
          && isAccessible(env, site)
          && notOverriddenIn(site, symbol);
      default:
        throw new IllegalStateException();
    }
  }

  private boolean notOverriddenIn(Symbol.TypeSymbol site, Symbol symbol) {
    // TODO see Javac
    return true;
  }

  /**
   * Is symbol inherited in given class?
   */
  @VisibleForTesting
  boolean isInheritedIn(Symbol symbol, Symbol.TypeSymbol clazz) {
    switch (symbol.flags() & Flags.ACCESS_FLAGS) {
      case Flags.PUBLIC:
        return true;
      case Flags.PRIVATE:
        return symbol.owner() == clazz;
      case Flags.PROTECTED:
        // TODO see Javac
        return true;
      case 0:
        // TODO see Javac
        Symbol.PackageSymbol thisPackage = symbol.packge();
        for (Symbol.TypeSymbol sup = clazz; sup != null && sup != clazz.owner(); sup = sup.getSuperclass()) {
          if (sup.packge() != thisPackage) {
            return false;
          }
        }
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  private boolean isProtectedAccessible(Symbol symbol, Symbol.TypeSymbol c, Symbol.TypeSymbol site) {
    // TODO see Javac
    return true;
  }

  public static class SymbolNotFound extends Symbol {
    public SymbolNotFound() {
      super(Symbol.ABSENT, 0, null, null);
    }
  }

  public static class AmbiguityErrorSymbol extends Symbol {
    public AmbiguityErrorSymbol() {
      super(Symbol.AMBIGUOUS, 0, null, null);
    }
  }

  public static class AccessErrorSymbol extends Symbol {
    /**
     * The invalid symbol found during resolution.
     */
    Symbol symbol;

    public AccessErrorSymbol(Symbol symbol) {
      super(Symbol.ERRONEOUS, 0, null, null);
      this.symbol = symbol;
    }
  }

}
