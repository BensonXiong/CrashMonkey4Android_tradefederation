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

package com.android.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Test that runs a native stress test executable on given device.
 * <p/>
 * It uses {@link NativeStressTestParser} to parse out number of iterations completed and report
 * those results to the {@link ITestInvocationListener}s.
 */
public class NativeStressTest extends AbstractRemoteTest implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "NativeStressTest";
    static final String DEFAULT_TEST_PATH = "data/nativestresstest";

    private ITestDevice mDevice = null;

    @Option(name = "native-stress-device-path",
      description="The path on the device where native stress tests are located.")
    private String mDeviceTestPath = DEFAULT_TEST_PATH;

    @Option(name = "stress-module-name",
            description="The name of the native test module to run. " +
            "If not specified all tests in --native-stress-device-path will be run")
    private String mTestModule = null;

    @Option(name = "iterations",
            description="The number of stress test iterations to run.")
    private int mNumIterations = -1;

    @Option(name = "max-iteration-time", description =
        "The maximum time to allow for one stress test iteration in ms. Default is 5 min.")
    private int mMaxIterationTime = 5 * 60 * 1000;

    // TODO: consider sharing code with {@link GTest}

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the Android native stress test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName(String moduleName) {
        return mTestModule;
    }

    /**
     * Gets the path where native stress tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mDeviceTestPath);
        if (mTestModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(mTestModule);
        }
        return testPath.toString();
    }

    /**
     * Executes all native stress tests in a folder as well as in all subfolders recursively.
     *
     * @param rootEntry The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listeners the run listeners
     * @throws DeviceNotAvailableException
     */
    private void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {

        if (rootEntry.isDirectory()) {
            // recursively run tests in all subdirectories
            for (IFileEntry childEntry : rootEntry.getChildren(true)) {
                doRunAllTestsInSubdirectory(childEntry, testDevice, listeners);
            }
        } else {
            // assume every file is a valid stress test binary.
            IShellOutputReceiver resultParser = createResultParser(rootEntry.getName(),
                    listeners);
            String fullPath = rootEntry.getFullEscapedPath();
            Log.i(LOG_TAG, String.format("Running native stress test %s on %s", fullPath,
                    mDevice.getSerialNumber()));
            // force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", fullPath));
            // -s is start iteration, -e means end iteration
            // use maxShellOutputResponseTime to enforce the max iteration time
            // it won't be exact, but should be close
            testDevice.executeShellCommand(String.format("%s -s 0 -e %d", fullPath,
                    mNumIterations-1), resultParser, mMaxIterationTime, 1);
        }
    }

    /**
     * Factory method for creating a {@link NativeStressTestParser} that parses test output and
     * forwards results to listeners.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param listeners
     * @param runName
     * @return a {@link IShellOutputReceiver}
     */
    IShellOutputReceiver createResultParser(String runName,
            Collection<ITestRunListener> listeners) {
        return new NativeStressTestParser(runName, listeners);
    }

    /**
     * {@inheritDoc}
     */
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        if (mNumIterations <= 0) {
            throw new IllegalArgumentException("number of iterations has not been set");
        }

        String testPath = getTestPath();
        IFileEntry nativeTestDirectory = mDevice.getFileEntry(testPath);
        if (nativeTestDirectory == null) {
            Log.w(LOG_TAG, String.format("Could not find native stress test directory %s in %s!",
                    testPath, mDevice.getSerialNumber()));
        }
        doRunAllTestsInSubdirectory(nativeTestDirectory, mDevice, convertListeners(listeners));
    }

    /**
     * Convert a list of {@link ITestInvocationListener} to a collection of {@link ITestRunListener}
     */
    private Collection<ITestRunListener> convertListeners(List<ITestInvocationListener> listeners) {
        ArrayList<ITestRunListener> copy = new ArrayList<ITestRunListener>(listeners.size());
        copy.addAll(listeners);
        return copy;
    }
}