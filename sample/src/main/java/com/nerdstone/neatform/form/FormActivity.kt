package com.nerdstone.neatform.form

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.nerdstone.neatandroidstepper.core.domain.StepperActions
import com.nerdstone.neatandroidstepper.core.model.StepperModel
import com.nerdstone.neatandroidstepper.core.stepper.Step
import com.nerdstone.neatandroidstepper.core.stepper.StepVerificationState
import com.nerdstone.neatform.FormType
import com.google.gson.Gson
import com.nerdstone.neatform.R
import com.nerdstone.neatform.utils.replaceView
import com.nerdstone.neatformcore.domain.builders.FormBuilder
import com.nerdstone.neatformcore.domain.model.JsonFormStepBuilderModel
import com.nerdstone.neatformcore.form.json.JsonFormBuilder
import timber.log.Timber


class FormActivity : AppCompatActivity(), StepperActions {
    private lateinit var formLayout: LinearLayout
    private lateinit var mainLayout: LinearLayout
    private lateinit var sampleToolBar: Toolbar
    private lateinit var pageTitleTextView: TextView
    private lateinit var exitFormImageView: ImageView
    private var formBuilder: FormBuilder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_activity)

        mainLayout = findViewById(R.id.mainLayout)
        formLayout = findViewById(R.id.formLayout)
        sampleToolBar = findViewById(R.id.sampleToolBar)
        pageTitleTextView = findViewById(R.id.pageTitleTextView)
        exitFormImageView = findViewById(R.id.exitFormImageView)

        val stepperModel = StepperModel.Builder()
            .exitButtonDrawableResource(R.drawable.ic_clear_white)
            .indicatorType(StepperModel.IndicatorType.DOT_INDICATOR)
            .toolbarColorResource(R.color.colorPrimaryDark)
            .build()


        if (intent.extras != null) {
            val formData = intent?.extras?.getSerializable("formData") as FormData
            pageTitleTextView.text = formData.formTitle
            exitFormImageView.setOnClickListener {
                if (it.id == R.id.exitFormImageView) {
                    finish()
                    Timber.d("Saved Data = %s",Gson().toJson(formBuilder?.getFormDetails()))
                }
            }

            val views = listOf<View>(
                layoutInflater.inflate(R.layout.sample_one_form_custom_layout, null)
            )
            when (formData.formCategory) {
                FormType.embeddableDefault -> {
                    formBuilder = JsonFormBuilder(this, formData.filePath, formLayout)
                        .buildForm()
                }
                FormType.embeddableCustomized -> {
                    formBuilder = JsonFormBuilder(this, formData.filePath, formLayout)
                        .buildForm(viewList = views)
                }
                FormType.stepperDefault -> {
                    sampleToolBar.visibility = View.GONE
                    formBuilder = JsonFormBuilder(this, formData.filePath, null)
                        .buildForm(JsonFormStepBuilderModel.Builder(this, stepperModel).build())
                    replaceView(mainLayout, (formBuilder as JsonFormBuilder).neatStepperLayout)
                }
                FormType.stepperCustomized -> {
                    sampleToolBar.visibility = View.GONE
                    formBuilder = JsonFormBuilder(this, formData.filePath, formLayout)
                        .buildForm(
                            JsonFormStepBuilderModel.Builder(this, stepperModel).build(),
                            views
                        )
                    replaceView(mainLayout, (formBuilder as JsonFormBuilder).neatStepperLayout)
                }
                else -> Toast.makeText(
                    this, "Please provide the right form type",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStepError(stepVerificationState: StepVerificationState) {
    }

    override fun onButtonNextClick(step: Step) {
    }

    override fun onButtonPreviousClick(step: Step) {
    }

    override fun onStepComplete(step: Step) {
        Toast.makeText(this, "Stepper completed", Toast.LENGTH_SHORT).show()
    }

    override fun onExitStepper() {
        val confirmCloseDialog = AlertDialog.Builder(this)
        confirmCloseDialog.apply {
            setTitle("Confirm close")
            setMessage("All the unsaved data will get lost if you quit")
            setPositiveButton("Exit") { _, _ -> finish() }
            setNegativeButton("Cancel") { _, _ -> return@setNegativeButton }
            create()
        }
        confirmCloseDialog.show()
    }

    override fun onCompleteStepper() {
        Toast.makeText(this, "Completed entire step with data "+Gson().toJson(formBuilder?.getFormDetails()), Toast.LENGTH_SHORT).show()
    }
}