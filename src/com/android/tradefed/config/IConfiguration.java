/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.config;

import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;

import java.util.Collection;
import java.util.List;

import junit.framework.Test;

/**
 * Configuration information for a TradeFederation invocation.
 *
 * Each TradeFederation invocation has a single {@link IConfiguration}. An {@link IConfiguration}
 * stores all the delegate objects that should be used during the invocation, and their associated
 * {@link Option}'s
 */
public interface IConfiguration {

    /**
     * Gets the {@link IBuildProvider} from the configuration.
     *
     * @return the {@link IBuildProvider} provided in the configuration
     */
    public IBuildProvider getBuildProvider();

    /**
     * Gets the {@link ITargetPreparer}s from the configuration.
     *
     * @return the {@link ITargetPreparer}s provided in order in the configuration
     */
    public List<ITargetPreparer> getTargetPreparers();

    /**
     * Gets the {@link Test}s to run from the configuration.
     *
     * @return the {@link Test}s provided in the configuration
     */
    public List<Test> getTests();

    /**
     * Gets the {@link ITestInvocationListener}s to use from the configuration.
     *
     * @return the {@link ITestInvocationListener}s provided in the configuration.
     */
    public List<ITestInvocationListener> getTestInvocationListeners();

    /**
     * Gets the {@link IDeviceRecovery} to use from the configuration.
     *
     * @return the {@link IDeviceRecovery} provided in the configuration.
     */
    public IDeviceRecovery getDeviceRecovery();

    /**
     * Gets the {@link ILeveledLogOutput} to use from the configuration.
     *
     * @return the {@link ILeveledLogOutput} provided in the configuration.
     */
    public ILeveledLogOutput getLogOutput();

    /**
     * Generic interface to get the configuration object with the given name.
     *
     * @param name the unique name of the configuration object
     * @param expectedType the expected object type
     *
     * @return the configuration object or <code>null</code> if the object type with given name
     * does not exist.
     */
    public Object getConfigurationObject(String name);

    /**
     * Similar to {@link #getConfigurationObject(String, Class)}, but for configuration
     * object types that support multiple objects.
     *
     * @param name the unique name of the configuration object
     * @param expectedType the expected object type
     *
     * @return the list of configuration objects or <code>null</code> if the object type with
     * given name does not exist.
     */
    public List<?> getConfigurationObjectList(String name);

    /**
     * Gets a copy of the list of all configuration objects.
     *
     * @return a {@link Collection} of all configuration objects
     */
    public Collection<Object> getAllConfigurationObjects();

    /**
     * Inject a option value into the set of configuration objects.
     * <p/>
     * Useful to provide values for options that are generated dynamically.
     *
     * @param optionName the option name
     * @param optionValue the option value
     * @throws ConfigurationException if failed to set the option's value
     */
    public void injectOptionValue(String optionName, String optionValue)
            throws ConfigurationException;

    /**
     * Create a copy of this object.
     *
     * @return a {link IConfiguration} copy
     */
    public IConfiguration clone();

    /**
     * Replace the current {@link IBuildProvider} in the configuration.
     *
     * @param provider the new {@link IBuildProvider}
     */
    public void setBuildProvider(IBuildProvider provider);

    /**
     * Set the {@link ILeveledLogOutput}, replacing any existing value.
     *
     * @param logger
     */
    public void setLogOutput(ILeveledLogOutput logger);

    /**
     * Set the {@link IDeviceRecovery}, replacing any existing value.
     *
     * @param recovery
     */
    public void setDeviceRecovery(IDeviceRecovery recovery);

    /**
     * Set the {@link ITargetPreparer}, replacing any existing value.
     *
     * @param preparer
     */
    public void setTargetPreparer(ITargetPreparer preparer);

    /**
     * Convenience method to set a single {@link Test} in this configuration, replacing any
     * existing values
     *
     * @param test
     */
    public void setTest(Test test);

    /**
     * Set the list of {@link Test}s in this configuration, replacing any
     * existing values
     *
     * @param tests
     */
    public void setTests(List<Test> test);

    /**
     * Set the list of {@link ITestInvocationListener}s, replacing any existing values
     *
     * @param listeners
     */
    public void setTestInvocationListeners(List<ITestInvocationListener> listeners);

    /**
     * Convenience method to set a single {@link ITestInvocationListener}
     *
     * @param listener
     */
    void setTestInvocationListener(ITestInvocationListener listener);

    /**
     * Generic method to set the config object with the given name, replacing any existing value.
     *
     * @param name the unique name of the config object type.
     * @param configObject the config object
     * @throws ConfigurationException if the configObject was not the correct type
     */
    public void setConfigurationObject(String name, Object configObject)
            throws ConfigurationException;

    /**
     * Generic method to set the config object list for the given name, replacing any existing
     * value.
     *
     * @param name the unique name of the config object type.
     * @param configList the config object list
     * @throws ConfigurationException if any objects in the list are not the correct type
     */
    void setConfigurationObjectList(String name, List<?> configList) throws ConfigurationException;
}
