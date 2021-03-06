package com.nerdstone.neatformcore.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.nerdstone.neatformcore.BuildConfig
import com.nerdstone.neatformcore.R
import com.nerdstone.neatformcore.domain.builders.FormBuilder
import com.nerdstone.neatformcore.domain.model.NFormViewData
import com.nerdstone.neatformcore.domain.model.NFormViewDetails
import com.nerdstone.neatformcore.domain.model.NFormViewProperty
import com.nerdstone.neatformcore.domain.view.NFormView
import com.nerdstone.neatformcore.domain.view.RootView
import com.nerdstone.neatformcore.viewmodel.DataViewModel
import timber.log.Timber
import java.util.*
import kotlin.reflect.KClass

const val ID = "id"
const val VALUE = "value"
const val VALIDATION_RESULT = "validationResults"

object ViewUtils {

    fun createViews(
        rootView: RootView, viewProperties: List<NFormViewProperty>,
        formBuilder: FormBuilder, buildFromLayout: Boolean = false
    ) {
        val registeredViews = formBuilder.registeredViews
        for (viewProperty in viewProperties) {
            val kClass = registeredViews[viewProperty.type]
            if (kClass.isNotNull()) {
                buildView(buildFromLayout, rootView, viewProperty, formBuilder, kClass!!)
            }
        }
    }

    private fun buildView(
        buildFromLayout: Boolean, rootView: RootView,
        viewProperty: NFormViewProperty, formBuilder: FormBuilder, kClass: KClass<*>
    ) {
        val androidView = rootView as View
        val context = rootView.context
        if (buildFromLayout) {
            val view = androidView.findViewById<View>(
                context.resources.getIdentifier(viewProperty.name, ID, context.packageName)
            )
            try {
                getView(view as NFormView, viewProperty, formBuilder)
            } catch (e: Exception) {
                val message =
                    "ERROR: The view with name ${viewProperty.name} defined in json form is missing in custom layout"
                Timber.e(e, message)
                if (BuildConfig.DEBUG) Toast.makeText(context, message, LENGTH_SHORT).show()
            }
        } else {
            val constructor = kClass.constructors.minBy { it.parameters.size }
            rootView.addChild(
                getView(constructor!!.call(context) as NFormView, viewProperty, formBuilder)
            )
        }
    }

    private fun getView(
        nFormView: NFormView, viewProperty: NFormViewProperty, formBuilder: FormBuilder
    ): NFormView {
        if (viewProperty.subjects != null) {
            val viewDispatcher = formBuilder.viewDispatcher
            viewDispatcher.run {
                rulesFactory.registerSubjects(splitText(viewProperty.subjects, ","), viewProperty)
                val hasVisibilityRule = rulesFactory.viewHasVisibilityRule(viewProperty)
                if (hasVisibilityRule) {
                    rulesFactory.rulesHandler.changeVisibility(
                        false, nFormView.viewDetails.view
                    )
                }
            }
        }
        return setupView(nFormView, viewProperty, formBuilder)
    }

    fun splitText(text: String?, delimiter: String): List<String> {
        return if (text == null || text.isEmpty()) {
            ArrayList()
        } else listOf(*text.split(delimiter.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
    }

    /**
     * This method appends a suffix '*' to the provided [text]
     */
    fun addRedAsteriskSuffix(text: String): SpannableString {
        if (text.isNotEmpty()) {
            val textWithSuffix = SpannableString("$text *")
            textWithSuffix.setSpan(
                ForegroundColorSpan(Color.RED), textWithSuffix.length - 1, textWithSuffix.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return textWithSuffix
        }
        return SpannableString(text)
    }

    fun getKey(name: String, suffix: String): String {
        return name.substringBefore(suffix)
    }

    fun setupView(
        nFormView: NFormView, viewProperty: NFormViewProperty, formBuilder: FormBuilder
    ): NFormView {
        with(nFormView) {

            //Set late init properties
            viewProperties = viewProperty
            dataActionListener = formBuilder.viewDispatcher
            formValidator = formBuilder.formValidator

            with(viewDetails) {
                name = viewProperty.name
                metadata = viewProperty.viewMetadata
                view.id = View.generateViewId()
                view.tag = viewProperty.name
            }

            addRequiredFields(nFormView)
            trackRequiredField()
            viewBuilder.buildView()
        }
        return nFormView
    }

    private fun addRequiredFields(nFormView: NFormView) {
        if (Utils.isFieldRequired(nFormView))
            nFormView.formValidator.requiredFields.add(nFormView.viewDetails.name)
    }

    /**
     * This method is the one responsible for mapping the JSON string [acceptedAttributes] to actual
     * view attributes to [nFormView]. [task] is a the method that is responsible for applying these
     * properties
     */
    fun applyViewAttributes(
        nFormView: NFormView, acceptedAttributes: HashSet<String>,
        task: (attribute: Map.Entry<String, Any>) -> Unit
    ) {
        nFormView.viewProperties.viewAttributes?.forEach { attribute ->
            if (acceptedAttributes.contains(attribute.key.toUpperCase(Locale.getDefault()))) {
                task(attribute)
            }
        }
    }

    fun applyCheckBoxStyle(context: Context, checkBox: CheckBox) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> checkBox.setTextAppearance(R.style.checkBoxStyle)
            else -> checkBox.setTextAppearance(context, R.style.checkBoxStyle)
        }
    }

    /**
     * This method extracts the value from the provided [attribute] and uses it as the text that will
     * be displayed on the top label of [nFormView]
     */
    fun addViewLabel(attribute: Pair<String, Any>, nFormView: NFormView): LinearLayout {
        val layout = View.inflate((nFormView as View).context, R.layout.custom_label_layout, null)
                as LinearLayout

        layout.findViewById<TextView>(R.id.labelTextView).apply {

            text = attribute.second as String

            if (Utils.isFieldRequired(nFormView)) {
                text = addRedAsteriskSuffix(text.toString())
            }
            error = null
        }
        (nFormView as View).findViewById<TextView>(R.id.errorMessageTextView)?.visibility =
            View.GONE
        return layout
    }

    /**
     * When a field represented by this [nFormView] is required it must be filled otherwise
     * data will not be valid and an empty map will be returned if you try to access the form data.
     */
    fun handleRequiredStatus(nFormView: NFormView) {
        val viewContext = getViewContext(nFormView.viewDetails)
        val dataViewModel =
            ViewModelProvider(viewContext as FragmentActivity)[DataViewModel::class.java]
        (nFormView as View).tag?.also {
            val formValidator = nFormView.formValidator
            if (Utils.isFieldRequired(nFormView) &&
                nFormView.viewDetails.value == null &&
                nFormView.viewDetails.view.visibility == View.VISIBLE &&
                !dataViewModel.details.value?.containsKey(nFormView.viewDetails.name)!!
            ) {
                formValidator.requiredFields.add(nFormView.viewDetails.name)
            } else {
                formValidator.requiredFields.remove(it)
            }
        }
    }

    /**
     * This method is used to animate the [view]
     */
    fun animateView(view: View) {
        when (view.visibility) {
            View.VISIBLE -> view.animate().alpha(1.0f).duration = 800
            View.GONE -> view.animate().alpha(0.0f).duration = 800
        }
    }

    /**
     * This method returns [DataViewModel] from the provided context of the [viewDetails]
     */
    fun getDataViewModel(viewDetails: NFormViewDetails) =
        ViewModelProvider(getViewContext(viewDetails) as FragmentActivity)[DataViewModel::class.java]

    /**
     * This method is used to get the base context for [viewDetails]. Sometimes the context is
     * wrapped inside a [ContextThemeWrapper] but we need its base context which is an [FragmentActivity]
     */
    private fun getViewContext(viewDetails: NFormViewDetails): Context {
        val context = viewDetails.view.context
        var activityContext = context
        if (context is ContextThemeWrapper) activityContext = context.baseContext
        return activityContext
    }

    @Throws(Throwable::class)
    fun findViewWithKey(key: String, context: Context): View? {
        val activityRootView =
            (context as Activity).findViewById<View>(android.R.id.content).rootView
        return activityRootView.findViewWithTag(key)
    }

    fun View.setReadOnlyState(enabled: Boolean) {
        isEnabled = enabled
        isFocusable = enabled
    }

    /**
     * This method updates the values of the fields with the provided [fieldValues]. Fields that should be disabled
     * are listed in the [readOnlyFields]
     */
    fun updateFieldValues(
        fieldValues: HashMap<String, NFormViewData>, context: Context,
        readOnlyFields: MutableSet<String>
    ) {
        fieldValues.filterNot { entry -> entry.key.endsWith(Constants.RuleActions.CALCULATION) }
            .forEach { entry ->
                val view: View? = findViewWithKey(entry.key, context)
                entry.value.value?.let { realValue ->
                    if (view != null) (view as NFormView).setValue(
                        realValue, !readOnlyFields.contains(entry.key)
                    )
                }
                Timber.d("Updated field %s : %s", entry.key, entry.value.value)
            }
    }

    /**
     * Crediting @see <a href='https://stackoverflow.com/questions/2586301/set-inputtype-for-an-edittext-programmatically?rq=1'>
     *     StackOverflow Implement InputType on EditText</a>
     */
    internal fun getSupportedEditTextTypes() =
        mapOf(
            "date" to (InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE),
            "datetime" to (InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_NORMAL),
            "none" to InputType.TYPE_NULL,
            "number" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL),
            "numberDecimal" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
            "numberPassword" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            "numberSigned" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED),
            "phone" to InputType.TYPE_CLASS_PHONE,
            "text" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL),
            "textAutoComplete" to InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE,
            "textAutoCorrect" to InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
            "textCapCharacters" to InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
            "textCapSentences" to InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            "textCapWords" to InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            "textEmailAddress" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            "textEmailSubject" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT),
            "textFilter" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER),
            "textLongMessage" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE),
            "textMultiLine" to InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            "textNoSuggestions" to InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            "textPassword" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            "textPersonName" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME),
            "textPhonetic" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PHONETIC),
            "textPostalAddress" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS),
            "textShortMessage" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),
            "textUri" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
            "textVisiblePassword" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            "textWebEditText" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT),
            "textWebEmailAddress" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
            "textWebPassword" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            "time" to (InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        )
}