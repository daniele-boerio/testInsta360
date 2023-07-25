package com.example.testapp.activity

import android.R
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.ToggleButton
import com.arashivision.insta360.basecamera.camera.CameraType
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import com.arashivision.sdkmedia.InstaMediaSDK
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.example.testapp.databinding.ActivityLiveBinding


class LiveActivity : ObserveCameraActivity(), IPreviewStatusListener {
    private lateinit var binding : ActivityLiveBinding
    private var mLayoutContent: ViewGroup? = null
    private var mCapturePlayerView: InstaCapturePlayerView? = null
    private var mBtnSwitch: ToggleButton? = null
    private var mRbNormal: RadioButton? = null
    private var mRbFisheye: RadioButton? = null
    private var mRbPerspective: RadioButton? = null
    private var mRbPlane: RadioButton? = null
    private var mSpinnerResolution: Spinner? = null
    private var mSpinnerStabType: Spinner? = null
    private var mCurrentResolution: PreviewStreamResolution? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //init InstaMediaSDK for preview
        InstaMediaSDK.init(this.application)

        bindViews()

        InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)
    }

    private fun bindViews() {
        mLayoutContent = binding.layoutContent
        mCapturePlayerView = binding.playerCapture
        mCapturePlayerView!!.setLifecycle(lifecycle)
        mBtnSwitch = binding.btnSwitch
        mBtnSwitch!!.setOnClickListener {
            if (mBtnSwitch!!.isChecked) {
                if (mCurrentResolution == null) {
                    InstaCameraManager.getInstance().startPreviewStream()
                } else {
                    InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution)
                }
            } else {
                InstaCameraManager.getInstance().closePreviewStream()
            }
        }
        mRbNormal = binding.rbNormal
        mRbFisheye = binding.rbFisheye
        mRbPerspective = binding.rbPerspective
        mRbPlane = binding.rbPlane
        val radioGroup: RadioGroup = binding.rgPreviewMode
        radioGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            // Need to restart the preview stream when switching between plane and others
            if (checkedId == binding.rbPlane.id) {
                InstaCameraManager.getInstance().closePreviewStream()
                if (mCurrentResolution == null) {
                    InstaCameraManager.getInstance().startPreviewStream()
                } else {
                    InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution)
                }
                mRbFisheye!!.isEnabled = false
                mRbPerspective!!.isEnabled = false
            } else if (checkedId == binding.rbNormal.id) {
                if (!mRbFisheye!!.isEnabled || !mRbPerspective!!.isEnabled) {
                    InstaCameraManager.getInstance().closePreviewStream()
                    if (mCurrentResolution == null) {
                        InstaCameraManager.getInstance().startPreviewStream()
                    } else {
                        InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution)
                    }
                    mRbFisheye!!.isEnabled = true
                    mRbPerspective!!.isEnabled = true
                } else {
                    // 切换到普通模式
                    // Switch to Normal Mode
                    mCapturePlayerView!!.switchNormalMode()
                }
            } else if (checkedId == binding.rbFisheye.id) {
                // 切换到鱼眼模式
                // Switch to Fisheye Mode
                mCapturePlayerView!!.switchFisheyeMode()
            } else if (checkedId == binding.rbPerspective.id) {
                // 切换到透视模式
                // Switch to Perspective Mode
                mCapturePlayerView!!.switchPerspectiveMode()
            }
        }
        mSpinnerResolution = binding.spinnerResolution
        val adapter1: ArrayAdapter<PreviewStreamResolution> =
            ArrayAdapter(this, R.layout.simple_spinner_dropdown_item)
        adapter1.addAll(
            InstaCameraManager.getInstance()
                .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL)
        )
        mSpinnerResolution!!.adapter = adapter1
        mSpinnerResolution!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                mCurrentResolution = adapter1.getItem(position)
                InstaCameraManager.getInstance().closePreviewStream()
                InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        mSpinnerStabType = binding.spinnerStabType
        val adapter2: ArrayAdapter<String> =
            ArrayAdapter(this, R.layout.simple_spinner_dropdown_item)
        adapter2.add(getString(com.example.testapp.R.string.stab_type_auto))
        adapter2.add(getString(com.example.testapp.R.string.stab_type_panorama))
        adapter2.add(getString(com.example.testapp.R.string.stab_type_calibrate_horizon))
        adapter2.add(getString(com.example.testapp.R.string.stab_type_footage_motion_smooth))
        adapter2.add(getString(com.example.testapp.R.string.stab_type_off))
        mSpinnerStabType!!.adapter = adapter2
        mSpinnerStabType!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                if ((position == 4 && mCapturePlayerView!!.isStabEnabled
                            || position != 4 && !mCapturePlayerView!!.isStabEnabled)
                ) {
                    InstaCameraManager.getInstance().closePreviewStream()
                    if (mCurrentResolution == null) {
                        InstaCameraManager.getInstance().startPreviewStream()
                    } else {
                        InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution)
                    }
                } else {
                    mCapturePlayerView!!.setStabType(stabType)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val isNanoS =
            TextUtils.equals(InstaCameraManager.getInstance().cameraType, CameraType.NANOS.type)
        mSpinnerStabType!!.visibility = if (isNanoS) View.GONE else View.VISIBLE
    }

    private val stabType: Int
        get() = when (mSpinnerStabType!!.selectedItemPosition) {
            0 -> InstaStabType.STAB_TYPE_AUTO
            1 -> InstaStabType.STAB_TYPE_PANORAMA
            2 -> InstaStabType.STAB_TYPE_CALIBRATE_HORIZON
            3 -> InstaStabType.STAB_TYPE_FOOTAGE_MOTION_SMOOTH
            else -> InstaStabType.STAB_TYPE_AUTO
        }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // 退出页面时需要关闭预览
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
            InstaCameraManager.getInstance().closePreviewStream()
            mCapturePlayerView!!.destroy()
        }
    }

    override fun onOpening() {
        // 预览开启中
        // Preview Opening
        mBtnSwitch!!.isChecked = true
    }

    override fun onOpened() {
        // 预览开启成功，可以播放预览流
        // Preview stream is on and can be played
        InstaCameraManager.getInstance().setStreamEncode()
        mCapturePlayerView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(mCapturePlayerView!!.pipeline)
            }

            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        mCapturePlayerView!!.prepare(createParams())
        mCapturePlayerView!!.play()
        mCapturePlayerView!!.keepScreenOn = true
    }

    private fun createParams(): CaptureParamsBuilder? {
        val builder = CaptureParamsBuilder()
            .setCameraType(InstaCameraManager.getInstance().cameraType)
            .setMediaOffset(InstaCameraManager.getInstance().mediaOffset)
            .setMediaOffsetV2(InstaCameraManager.getInstance().mediaOffsetV2)
            .setMediaOffsetV3(InstaCameraManager.getInstance().mediaOffsetV3)
            .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie)
            .setGyroTimeStamp(InstaCameraManager.getInstance().gyroTimeStamp)
            .setBatteryType(InstaCameraManager.getInstance().batteryType)
            .setStabType(stabType)
            .setStabEnabled(mSpinnerStabType!!.selectedItemPosition != 4)
        if (mCurrentResolution != null) {
            builder.setResolutionParams(
                mCurrentResolution!!.width,
                mCurrentResolution!!.height,
                mCurrentResolution!!.fps
            )
        }
        if (mRbPlane!!.isChecked) {
            // 平铺模式
            // Plane Mode
            builder.setRenderModelType(CaptureParamsBuilder.RENDER_MODE_PLANE_STITCH)
                .setScreenRatio(2, 1)
        } else {
            // 普通模式
            // Normal Mode
            builder.renderModelType = CaptureParamsBuilder.RENDER_MODE_AUTO
        }
        return builder
    }

    override fun onIdle() {
        // 预览已停止
        // Preview Stopped
        mCapturePlayerView!!.destroy()
        mCapturePlayerView!!.keepScreenOn = false
    }

    override fun onError() {
        // 预览开启失败
        // Preview Failed
        mBtnSwitch!!.isChecked = false
    }
}