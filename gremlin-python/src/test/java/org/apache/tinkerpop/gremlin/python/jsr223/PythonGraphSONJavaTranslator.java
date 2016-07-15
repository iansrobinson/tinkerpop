/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.tinkerpop.gremlin.python.jsr223;

import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.process.traversal.Translator;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.verification.VerificationException;
import org.apache.tinkerpop.gremlin.process.traversal.util.EmptyTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.util.JavaTranslator;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader;
import org.apache.tinkerpop.gremlin.util.ScriptEngineCache;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.io.ByteArrayInputStream;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PythonGraphSONJavaTranslator<S extends TraversalSource, T extends Traversal.Admin<?, ?>> implements Translator.StepTranslator<S, T> {

    private final PythonTranslator pythonTranslator;
    private final JavaTranslator<S, T> javaTranslator;
    private final GraphSONReader reader = GraphSONReader.build().create();

    public PythonGraphSONJavaTranslator(final PythonTranslator pythonTranslator, final JavaTranslator<S, T> javaTranslator) {
        this.pythonTranslator = pythonTranslator;
        this.javaTranslator = javaTranslator;
    }

    @Override
    public S getTraversalSource() {
        return this.javaTranslator.getTraversalSource();
    }

    @Override
    public Class getAnonymousTraversal() {
        return this.javaTranslator.getAnonymousTraversal();
    }

    @Override
    public String getTargetLanguage() {
        return this.javaTranslator.getTargetLanguage();
    }

    @Override
    public T translate(final Bytecode bytecode) {

        for (final Bytecode.Instruction instruction : bytecode.getStepInstructions()) {
            for (final Object argument : instruction.getArguments()) {
                if (argument.toString().contains("$"))
                    throw new VerificationException("Lambdas are currently not supported: " + bytecode, EmptyTraversal.instance());
            }
        }

        try {
            final ScriptEngine jythonEngine = ScriptEngineCache.get("jython");
            jythonEngine.getBindings(ScriptContext.ENGINE_SCOPE)
                    .put(this.pythonTranslator.getTraversalSource(), jythonEngine.eval("RemoteGraph(None).traversal()"));
            final String graphsonBytecode = jythonEngine.eval("GraphSONSerializer.serialize(" + this.pythonTranslator.translate(bytecode) + ")").toString();
            //System.out.println(graphsonBytecode + "!!!!");
            return this.javaTranslator.translate(this.reader.readObject(new ByteArrayInputStream(graphsonBytecode.getBytes()), Bytecode.class));
        } catch (final Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

    }
}
