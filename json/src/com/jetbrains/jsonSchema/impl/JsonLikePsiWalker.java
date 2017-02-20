/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Irina.Chernushina on 2/15/2017.
 */
public interface JsonLikePsiWalker {
  ExtensionPointName<JsonLikePsiWalker> EXTENSION_POINT_NAME = ExtensionPointName.create("Json.Like.Psi.Walker");
  boolean handles(@NotNull PsiElement element);
  boolean isName(PsiElement checkable);
  boolean isPropertyWithValue(@NotNull PsiElement element);
  PsiElement goUpToCheckable(@NotNull final PsiElement element);
  List<JsonSchemaWalker.Step> findPosition(@NotNull final PsiElement element, boolean isName, boolean forceLastTransition);
  boolean isNameQuoted();
  boolean hasPropertiesBehind(@NotNull PsiElement element);
  Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement element);
}
