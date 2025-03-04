/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.build.gradle.task

import com.tencent.tinker.build.gradle.Compatibilities
import com.tencent.tinker.build.gradle.TinkerBuildPath
import com.tencent.tinker.build.util.FileOperation
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import proguard.gradle.plugin.android.ProGuardTransform
import proguard.gradle.plugin.android.dsl.ProGuardAndroidExtension

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
class TinkerProguardConfigAction implements Action<Task> {
    static final String PROGUARD_CONFIG_SETTINGS =
            "-keepattributes *Annotation* \n" +
                    "-dontwarn com.tencent.tinker.anno.AnnotationProcessor \n" +
                    "-keep @com.tencent.tinker.anno.DefaultLifeCycle public class *\n" +
                    "-keep public class * extends android.app.Application {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.entry.ApplicationLifeCycle {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class * implements com.tencent.tinker.entry.ApplicationLifeCycle {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.loader.TinkerLoader {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class * extends com.tencent.tinker.loader.TinkerLoader {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.loader.TinkerTestDexLoad {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.entry.TinkerApplicationInlineFence {\n" +
                    "    *;\n" +
                    "}\n"

    def applicationVariant

    TinkerProguardConfigAction(variant) {
        applicationVariant = variant
    }

    @Override
    void execute(Task task) {
        updateTinkerProguardConfig(task.getProject())
    }

    def updateTinkerProguardConfig(project) {
        def file = project.file(TinkerBuildPath.getProguardConfigPath(project))
        project.logger.error("try update tinker proguard file with ${file}")

        // Create the directory if it doesnt exist already
        file.getParentFile().mkdirs()

        // Write our recommended proguard settings to this file
        FileWriter fr = new FileWriter(file.path)

        String applyMappingFile = project.extensions.tinkerPatch.buildConfig.applyMapping

        //write applymapping
        if (FileOperation.isLegalFile(applyMappingFile)) {
            project.logger.error("try add applymapping ${applyMappingFile} to build the package")
            fr.write("-applymapping " + applyMappingFile)
            fr.write("\n")
        } else {
            project.logger.error("applymapping file ${applyMappingFile} is illegal, just ignore")
        }

        fr.write(PROGUARD_CONFIG_SETTINGS)

        fr.write("#your dex.loader patterns here\n")
        //they will removed when apply
        Iterable<String> loader = project.extensions.tinkerPatch.dex.loader
        for (String pattern : loader) {
            if (pattern.endsWith("*") && !pattern.endsWith("**")) {
                pattern += "*"
            }
            fr.write("-keep class " + pattern)
            fr.write("\n")
        }
        fr.close()

        // Add this proguard settings file to the list
        injectTinkerProguardRuleFile(project, file)
    }

    private void injectTinkerProguardRuleFile(project, file) {
        def agpObfuscateTask = Compatibilities.getObfuscateTask(project, applicationVariant)
        def configurationFilesOwner = null
        def configurationFilesField = null
        try {
            configurationFilesOwner = agpObfuscateTask
            configurationFilesField = Compatibilities.getFieldRecursively(configurationFilesOwner.getClass(), '__configurationFiles__')
        } catch (Throwable ignored) {
            configurationFilesOwner = null
            configurationFilesField = null
        }
        if (configurationFilesField == null) {
            try {
                configurationFilesOwner = agpObfuscateTask
                configurationFilesField = Compatibilities.getFieldRecursively(configurationFilesOwner.getClass(), 'configurationFiles')
            } catch (Throwable ignored) {
                configurationFilesOwner = null
                configurationFilesField = null
            }
        }
        if (configurationFilesField == null) {
            try {
                configurationFilesOwner = agpObfuscateTask.transform
                configurationFilesField = Compatibilities.getFieldRecursively(configurationFilesOwner.getClass(), 'configurationFiles')
            } catch (Throwable ignored) {
                configurationFilesOwner = null
                configurationFilesField = null
            }
        }

        def agpConfigurationFiles = null
        boolean isOK = false
        if (configurationFilesOwner != null && configurationFilesField != null) {
            try {
                agpConfigurationFiles = configurationFilesField.get(configurationFilesOwner)
                isOK = true
            } catch (Throwable ignored) {
                isOK = false
            }
        }

        if (isOK) {
            def mergedConfigurationFiles = project.files(agpConfigurationFiles, project.files(file))
            try {
                configurationFilesField.set(configurationFilesOwner, mergedConfigurationFiles)
                def mergedConfigurationFilesForConfirm = configurationFilesField.get(configurationFilesOwner)
                println "Now proguard rule files are: ${mergedConfigurationFilesForConfirm.files}"
            } catch (Throwable ignored) {
                isOK = false
            }
        }
        // proguard-gradle 适配插入自定义混淆文件
        if (!isOK) {
            try {
                ProGuardTransform proGuardTransform = agpObfuscateTask.transform
                def proguardBlockDeclaredField = Compatibilities.getFieldRecursively(proGuardTransform.class, 'proguardBlock')
                ProGuardAndroidExtension proGuardAndroidExtension = proguardBlockDeclaredField.get(proGuardTransform)
                def variantName = applicationVariant.name
                def variantConfiguration = proGuardAndroidExtension.configurations.findByName(variantName)
                variantConfiguration.configuration(file.absolutePath)
                println "proguard-gradle : Now proguard rule files are: ${variantConfiguration.configurations}"
                isOK = true
            } catch (Throwable ignore) {
                ignore.printStackTrace()
            }
        }

        if (!isOK) {
            throw new GradleException('Fail to inject tinker proguard rules file. Some compatibility works need to be done.')
        }
    }
}