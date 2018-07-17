package com.davipb.jamspell;

import com.davipb.jamspell.jni.JamSpellJNI;
import com.davipb.jamspell.jni.NativeSpellCorrector;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * The main JamSpell class, capable of detecting and fixing spelling mistakes.
 */
public final class SpellCorrector {

    static { JamSpellMemoryManager.initialize(); }

    private final NativeSpellCorrector corrector = new NativeSpellCorrector();
    /**
     * The penalty applied to known words.
     *
     * @implNote The actual value of this field is stored in native code, but the native class
     * doesn't offer a way to get such value, only a way to set it. As such, this is just a
     * convenience field, and its default value must be updated if it is changed in the native code.
     */
    private @Getter double knownWordsPenalty = 20;
    /**
     * The penalty applied to unknown words.
     *
     * @implNote The actual value of this field is stored in native code, but the native class
     * doesn't offer a way to get such value, only a way to set it. As such, this is just a
     * convenience field, and its default value must be updated if it is changed in the native code.
     */
    private @Getter double unknownWordsPenalty = 20;
    /**
     * The maximum number of candidates to check when using {@link #fixFragment} and {@link #getCandidates}.
     *
     * @implNote The actual value of this field is stored in native code, but the native class
     * doesn't offer a way to get such value, only a way to set it. As such, this is just a
     * convenience field, and its default value must be updated if it is changed in the native code.
     */
    private @Getter int maxCandidatesToCheck = 14;

    {
        JamSpellMemoryManager.manage(
            corrector,
            NativeSpellCorrector.getCPtr(corrector),
            JamSpellJNI::delete_NativeSpellCorrector
        );
    }

    /** @see #getKnownWordsPenalty() */
    public void setKnownWordsPenalty(double knownWordsPenalty) {
        this.knownWordsPenalty = knownWordsPenalty;
        corrector.setPenalty(this.knownWordsPenalty, unknownWordsPenalty);
    }

    /** @see #getUnknownWordsPenalty() */
    public void setUnknownWordsPenalty(double unknownWordsPenalty) {
        this.unknownWordsPenalty = unknownWordsPenalty;
        corrector.setPenalty(this.knownWordsPenalty, unknownWordsPenalty);
    }

    /** @see #getMaxCandidatesToCheck() */
    public void setMaxCandidatesToCheck(int maxCandidatesToCheck) {
        this.maxCandidatesToCheck = maxCandidatesToCheck;
        corrector.setMaxCandiatesToCheck(this.maxCandidatesToCheck);
    }

    /**
     * Loads a pre-trained language model from an input stream. The stream will be saved to
     * a temporary file before being sent to the JamSpell engine.
     * <p>
     * Please note that JamSpell model files are currently not cross-platform, meaning that
     * you must load a model that was trained from the same native library that is being used
     * in the current platform.
     * Trying to load a model from a different platform will cause the program to hang
     * indefinitely.
     *
     * @param stream The stream of the language model.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     */
    public void loadLangModel(@NonNull InputStream stream) throws JamSpellException {
        try {
            val temp = Files.createTempFile("jamspell-", ".bin");
            Files.copy(stream, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            loadLangModel(temp);
        } catch (IOException e) {
            throw new JamSpellException("Unable to save InputStream to temporary file", e);
        }
    }

    /**
     * Loads a pre-trained language model from a file on disk.
     * <p>
     * Please note that JamSpell model files are currently not cross-platform, meaning that
     * you must load a model that was trained from the same native library that is being used
     * in the current platform.
     * Trying to load a model from a different platform will cause the program to hang
     * indefinitely.
     *
     * @param modelPath The language model file.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     */
    public void loadLangModel(@NonNull Path modelPath) throws JamSpellException {
        if (Files.notExists(modelPath)) throw new JamSpellException("Model '" + modelPath + "' doesn't exit");
        if (Files.isDirectory(modelPath)) throw new JamSpellException("Model '" + modelPath + "' is not a file");

        val success = corrector.loadLangModel(modelPath.toString());
        if (!success) throw new JamSpellException("Unable to load model");
    }

    /**
     * Loads a pre-trained language model from a classpath resource. The resource will be extracted to a temporary
     * file before being sent to the native JamSpell engine.
     * <p>
     * Please note that JamSpell model files are currently not cross-platform, meaning that
     * you must load a model that was trained from the same native library that is being used
     * in the current platform.
     * Trying to load a model from a different platform will cause the program to hang
     * indefinitely.
     *
     * @param resourceName The name of the resource. Must be an absolute name.
     * @throws JamSpellException When the resource doesn't exist or the JamSpell engine is unable to load the model.
     */
    public void loadResourceLangModel(@NonNull String resourceName) throws JamSpellException {
        val resource = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (resource == null) throw new JamSpellException("Resource model '" + resourceName + "' doesn't exist");

        loadLangModel(resource);
    }

    /**
     * Loads a platform-specific pre-trained model from a classpath resource.  The resource will be extracted to a temporary
     * file before being sent to the native JamSpell engine. The final resource path is derived by appending
     * {@link PlatformUtils#PLATFORM_PATH} to the root path, then appending {@code model.bin} to it.
     *
     * @param root The root resource path from which to derive the resource name.
     */
    public void loadPlatformResourceLangModel(@NonNull String root) {
        loadPlatformResourceLangModel(root, "model.bin");
    }

    /**
     * Generates the locale tree for a given locale, which includes the locale itself and all of its parent locales,
     * in order of most specific to least specific.
     *
     * @param locale The starting locale of the tree.
     * @return The locale tree.
     */
    private @NotNull Iterable<@NotNull Locale> localeTree(@NonNull Locale locale) {
        val result = new ArrayList<Locale>();

        while (locale != null) {
            result.add(locale);
            if (!locale.getVariant().equals("")) locale = new Locale(locale.getLanguage(), locale.getCountry());
            else if (!locale.getCountry().equals("")) locale = new Locale(locale.getLanguage());
            else locale = null;
        }

        return result;
    }

    /**
     * Loads a platform-specific pre-trained model from a classpath resource.  The resource will be extracted to a temporary
     * file before being sent to the native JamSpell engine. The final resource path is derived by appending
     * {@link PlatformUtils#PLATFORM_PATH} to the root path, then appending the resource name to that.
     *
     * @param root The root resource path from which to derive the resource name.
     * @param name The name of the resource file.
     */
    public void loadPlatformResourceLangModel(@NonNull String root, @NonNull String name) {
        loadResourceLangModel(PlatformUtils.makeResourcePlatformSpecific(root, name));
    }

    /**
     * Loads a standard pre-trained model for the default locale. If the default locale does not have a standard model, all of
     * its parent locales are searched before an error is thrown. For example, the locale "en-US-east" will cause
     * "en-US" and "en" to be searched.
     *
     * @throws JamSpellException When there is no standard model for the default locale or the JamSpell engine
     *                           was unable to load the model.
     */
    public void loadStandardLangModel() throws JamSpellException {
        loadStandardLangModel(Locale.getDefault());
    }

    /**
     * Loads the standard pre-trained model for a locale. If the specified locale does not have a standard model, all of
     * its parent locales are searched before an error is thrown. For example, the locale "en-US-east" will cause
     * "en-US" and "en" to be searched.
     *
     * @param locale The locale of the model to be loaded.
     * @throws JamSpellException When there is no standard model for the specified locale or the JamSpell engine
     *                           was unable to load the model.
     */
    public void loadStandardLangModel(@NonNull Locale locale) throws JamSpellException {
        val root = "com/davipb/jamspell";
        val loader = getClass().getClassLoader();

        for (val current : localeTree(locale)) {
            val name = current.toLanguageTag() + ".bin";
            val resourceName = PlatformUtils.makeResourcePlatformSpecific(root, name);
            val resource = loader.getResourceAsStream(resourceName);

            if (resource != null) {
                loadLangModel(resource);
                return;
            }
        }

        throw new JamSpellException("No standard model for locale '" + locale.toLanguageTag() + "' or any of its parents");
    }

    /**
     * Loads a pre-trained language model from a file on disk.
     * <p>
     * Please note that JamSpell model files are currently not cross-platform, meaning that
     * you must load a model that was trained from the same native library that is being used
     * in the current platform.
     * Trying to load a model from a different platform will cause the program to hang
     * indefinitely.
     *
     * @param modelPath The path to the language model file.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     */
    public void loadLangModel(@NonNull String modelPath) throws JamSpellException {
        loadLangModel(Paths.get(modelPath));
    }

    /**
     * Loads a platform-specific model. The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}
     * using {@code model.bin} as the name.
     *
     * @param root The root directory from which to derive the path of the model.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     */
    public void loadPlatformLangModel(@NonNull String root) throws JamSpellException {
        loadPlatformLangModel(Paths.get(root));
    }


    /**
     * Loads a platform-specific model. The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific},
     * using the current working directory as the root and {@code model.bin} as the name.
     *
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     * @see PlatformUtils#makePathPlatformSpecific
     */
    public void loadPlatformLangModel() throws JamSpellException {
        loadPlatformLangModel(Paths.get(""), "model.bin");
    }

    /**
     * Loads a platform-specific model. The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific},
     * using {@code model.bin} as the name.
     *
     * @param root The root directory from which to derive the path of the model.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     * @see PlatformUtils#makePathPlatformSpecific
     */
    public void loadPlatformLangModel(@NonNull Path root) throws JamSpellException {
        loadPlatformLangModel(root, "model.bin");
    }

    /**
     * Loads a platform-specific model. The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}.
     *
     * @param root The root directory from which to derive the path of the model.
     * @param name The file name of the model inside the derived directory path.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     * @see PlatformUtils#makePathPlatformSpecific
     */
    public void loadPlatformLangModel(@NonNull String root, @NonNull String name) throws JamSpellException {
        loadPlatformLangModel(Paths.get(root), name);
    }

    /**
     * Loads a platform-specific model. The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}.
     *
     * @param root The root directory from which to derive the path of the model.
     * @param name The file name of the model inside the derived directory path.
     * @throws JamSpellException When the JamSpell engine is unable to load the model.
     * @see PlatformUtils#makePathPlatformSpecific
     */
    public void loadPlatformLangModel(@NonNull Path root, @NonNull String name) throws JamSpellException {
        loadLangModel(PlatformUtils.makePathPlatformSpecific(root, name));
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a temporary file.
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel(String)}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainLangModel(@NonNull String dataPath, @NonNull String alphabetPath) throws JamSpellException {
        trainLangModel(Paths.get(dataPath), Paths.get(alphabetPath));
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a model file.
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelPath    The path of the file where the trained model will be saved.
     *                     This file and all its parent directories will be created if they don't exist.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainLangModel(@NonNull String dataPath, @NonNull String alphabetPath, @NonNull String modelPath) throws JamSpellException {
        trainLangModel(Paths.get(dataPath), Paths.get(alphabetPath), Paths.get(modelPath));
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a temporary file.
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel(String)}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainLangModel(@NonNull Path dataPath, @NonNull Path alphabetPath) throws JamSpellException {

        final Path modelPath;
        try {
            modelPath = Files.createTempFile("jamspell-", ".bin");
            modelPath.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new JamSpellException("Unable to create temporary model file", e);
        }

        trainLangModel(dataPath, alphabetPath, modelPath);
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}, using the current working
     * directory as the root and {@code model.bin} as the name.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull String dataPath, @NonNull String alphabetPath) {
        trainPlatformLangModel(Paths.get(dataPath), Paths.get(alphabetPath));
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}, using {@code model.bin}
     * as the name.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelRoot    The base directory where the model will be saved.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull String dataPath, @NonNull String alphabetPath, @NonNull String modelRoot) {
        trainPlatformLangModel(Paths.get(dataPath), Paths.get(alphabetPath), Paths.get(modelRoot));
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelRoot    The base directory where the model will be saved.
     * @param modelName    The name of the generated model file.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull String dataPath, @NonNull String alphabetPath, @NonNull String modelRoot, @NonNull String modelName) {
        trainPlatformLangModel(Paths.get(dataPath), Paths.get(alphabetPath), Paths.get(modelRoot), modelName);
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}, using the current working
     * directory as the root and {@code model.bin} as the name.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull Path dataPath, @NonNull Path alphabetPath) {
        trainPlatformLangModel(dataPath, alphabetPath, Paths.get(""), "model.bin");
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}, using {@code model.bin}
     * as the name.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelRoot    The base directory where the model will be saved.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull Path dataPath, @NonNull Path alphabetPath, @NonNull Path modelRoot) {
        trainPlatformLangModel(dataPath, alphabetPath, modelRoot, "model.bin");
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a platform-specific model file.
     * The exact location of the model is given by {@link PlatformUtils#makePathPlatformSpecific}.
     * <p>
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelRoot    The base directory where the model will be saved.
     * @param modelName    The name of the generated model file.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainPlatformLangModel(@NonNull Path dataPath, @NonNull Path alphabetPath, @NonNull Path modelRoot, @NonNull String modelName) {
        val modelPath = PlatformUtils.makePathPlatformSpecific(modelRoot, modelName);
        trainLangModel(dataPath, alphabetPath, modelPath);
    }

    /**
     * Trains a new language model in this spell corrector and saves its results to a model file.
     * This operation is quite heavy and time-consuming, and, as such, it is recommended to pre-train models and
     * ship the model files to be loaded with {@link #loadLangModel}, a much lighter operation.
     * <p>
     * If training models at runtime is inevitable for your application, considering running this method in a separate thread.
     *
     * @param dataPath     The path of the file containing the training data.
     * @param alphabetPath The path of the file containing the alphabet for the target language. Only characters included
     *                     in the alphabet will be considered for the purposes of the training.
     * @param modelPath    The path of the file where the trained model will be saved.
     *                     This file and all its parent directories will be created if they don't exist.
     * @throws JamSpellException When the JamSpell engine is unable to train a new model.
     */
    public void trainLangModel(@NonNull Path dataPath, @NonNull Path alphabetPath, @NonNull Path modelPath) throws JamSpellException {
        if (Files.notExists(dataPath)) throw new JamSpellException("Training data '" + dataPath + "' doesn't exist");
        if (Files.notExists(alphabetPath))
            throw new JamSpellException("Training alphabet '" + alphabetPath + "' doesn't exist");

        if (Files.isDirectory(dataPath)) throw new JamSpellException("Training data '" + dataPath + "' is not a file");
        if (Files.isDirectory(alphabetPath))
            throw new JamSpellException("Training alphabet '" + alphabetPath + "' is not a file");
        if (Files.isDirectory(modelPath))
            throw new JamSpellException("Training result model '" + modelPath + "' is not a file");

        try {
            Files.createDirectories(modelPath.getParent());
        } catch (IOException e) {
            throw new JamSpellException("Unable to create parent directory for model '" + modelPath + "'", e);
        }

        val success = corrector.trainLangModel(dataPath.toString(), alphabetPath.toString(), modelPath.toString());
        if (!success) throw new JamSpellException("The native JamSpell engine was unable to train");
    }

    /**
     * Gets all candidates for spellchecking for a word in a sentence, in descending order of certainty, such as that
     * the first item in the returned list is the one that would be used in {@link #fixFragment}.
     *
     * @param sentence The full sentence, split into words.
     * @param position The 0-based index of the word inside the sentence to find spellchecking candidates for.
     * @return All candidates for spellchecking of the specified word. If the first item of this list
     * is the same as the word itself, then the spellchecker doesn't believe that word has a typo.
     */
    public @NotNull List<@NotNull String> getCandidates(@NonNull Iterable<@NotNull String> sentence, int position) {
        val nativeSentence = StringVectorUtils.toNative(sentence);
        val nativeResult = corrector.getCandidates(nativeSentence, position);

        val result = StringVectorUtils.fromNative(nativeResult);

        nativeSentence.delete();
        nativeResult.delete();

        return result;
    }

    /**
     * Gets all candidates for spellchecking for a word in a sentence, in descending order of certainty, such as that
     * the first item in the returned list is the one that would be used in {@link #fixFragment}.
     *
     * @param sentence The full sentence, split into words.
     * @param position The 0-based index of the word inside the sentence to find spellchecking candidates for.
     * @return All candidates for spellchecking of the specified word. If the first item of this list
     * is the same as the word itself, then the spellchecker doesn't believe that word has a typo.
     */
    public @NotNull List<@NotNull String> getCandidates(@NonNull String[] sentence, int position) {
        return getCandidates(Arrays.asList(sentence), position);
    }

    /**
     * Gets all candidates for spellchecking for a word in a sentence, in descending order of certainty, such as that
     * the first item in the returned list is the one that would be used in {@link #fixFragment}.
     *
     * @param sentence The full sentence, split into words.
     * @param position The 0-based index of the word inside the sentence to find spellchecking candidates for.
     * @return All candidates for spellchecking of the specified word. If the first item of this list
     * is the same as the word itself, then the spellchecker doesn't believe that word has a typo.
     */
    public @NotNull List<@NotNull String> getCandidates(int position, @NonNull String... sentence) {
        return getCandidates(sentence, position);
    }

    /**
     * Fixes a fragment of text, replacing all words that are considered typos with their most
     * appropriate fixes. The resulting text is not normalized.
     *
     * @param text The text to be fixed.
     * @return The fixed fragment of text.
     */
    public @NotNull String fixFragment(@NonNull String text) {
        return fixFragment(text, false);
    }

    /**
     * Fixes a fragment of text, replacing all words that are considered typos with their most
     * appropriate fixes.
     *
     * @param text      The text to be fixed.
     * @param normalize If true, the resulting sentence will be normalize in letter casing and punctuation.
     * @return The fixed fragment of text.
     */
    public @NotNull String fixFragment(@NonNull String text, boolean normalize) {
        if (normalize) return corrector.fixFragmentNormalized(text);
        return Objects.requireNonNull(corrector.fixFragment(text));
    }
}
