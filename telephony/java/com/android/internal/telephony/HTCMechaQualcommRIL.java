/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2012 The LiquidSmooth Project
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

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

/**
 * Qualcomm RIL class for basebands that do not send the SIM status
 * piggybacked in RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED. Instead,
 * these radios will send radio state and we have to query for SIM
 * Custom Qualcomm SIM/RUIM Ready RIL for HTC Mecha (ThunderBolt)
 *
 * {@hide}
 */
public class HTCMechaQualcommRIL extends QualcommSharedRIL implements CommandsInterface {
    private final int RIL_INT_RADIO_OFF = 0;
    private final int RIL_INT_RADIO_UA = 1;
    private final int RIL_INT_RADIO_ON = 13;

    public HTCMechaQualcommRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    public void
    iccIO(int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        String IccAid;
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SIM_IO, result);
        IccCardStatus status = new IccCardStatus();

        if (path != null) {
            if (path.indexOf("7F20") != -1) {
                IccAid = mAid;
                path = path = path.replaceAll("7F20","7FFF");
            } else {
                IccAid = mAid;
            }
        } else {
            IccAid = mAid;
        }

        rr.mp.writeInt(command);
        rr.mp.writeInt(fileid);
        rr.mp.writeString(path);
        rr.mp.writeInt(p1);
        rr.mp.writeInt(p2);
        rr.mp.writeInt(p3);
        rr.mp.writeString(data);
        rr.mp.writeString(pin2);
        rr.mp.writeString(IccAid);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> iccIO: "
                    + " aid: " + IccAid + " "
                    + requestToString(rr.mRequest)
                    + " 0x" + Integer.toHexString(command)
                    + " 0x" + Integer.toHexString(fileid) + " "
                    + " path: " + path + ","
                    + p1 + "," + p2 + "," + p3);

        send(rr);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplication ca;

        boolean subscriptionFromSource = needsOldRilFeature("subscriptionFromSource");

        IccCardStatus status = new IccCardStatus();
        status.setCardState(p.readInt());
        status.setUniversalPinState(p.readInt());
        status.setGsmUmtsSubscriptionAppIndex(p.readInt());
        status.setCdmaSubscriptionAppIndex(p.readInt());
        status.setImsSubscriptionAppIndex(p.readInt());
        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        status.setNumApplications(numApplications);

        for (int i = 0 ; i < numApplications ; i++) {
            ca = new IccCardApplication();
            ca.app_type       = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state      = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            if ((ca.app_state == IccCardApplication.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_READY) ||
                (ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                ca.app_state = IccCardApplication.AppState.APPSTATE_UNKNOWN;
                Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
            }
            ca.aid            = p.readString();
            ca.app_label      = p.readString();
            ca.pin1_replaced  = p.readInt();
            ca.pin1           = ca.PinStateFromRILInt(p.readInt());
            ca.pin2           = ca.PinStateFromRILInt(p.readInt());
            status.addApplication(ca);
        }

        if (subscriptionFromSource)
            return status;

        int appIndex = -1;
        if (mPhoneType == RILConstants.CDMA_PHONE && !skipCdmaSubcription) {
            appIndex = status.getCdmaSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
        } else {
            appIndex = status.getGsmUmtsSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
        }

        if (numApplications > 0) {
            IccCardApplication application = status.getApplication(appIndex);
            mAid = application.aid;
            mSetPreferredNetworkType = mPreferredNetworkType;
            mUSIM = application.app_type == IccCardApplication.AppType.APPTYPE_USIM;

            if (TextUtils.isEmpty(mAid))
                mAid = "";
        }

        return status;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 14;
        int response[];

        /* HTC signal strength format:
         * 0: GW_SignalStrength
         * 1: GW_SignalStrength.bitErrorRate
         * 2: CDMA_SignalStrength.dbm
         * 3: CDMA_SignalStrength.ecio
         * 4: EVDO_SignalStrength.dbm
         * 5: EVDO_SignalStrength.ecio
         * 6: EVDO_SignalStrength.signalNoiseRatio
         * 7: ATT_SignalStrength.dbm
         * 8: ATT_SignalStrength.ecno
         * 9: LTE_SignalStrength.signalStrength
         * 10: LTE_SignalStrength.rsrp
         * 11: LTE_SignalStrength.rsrq
         * 12: LTE_SignalStrength.rssnr
         * 13: LTE_SignalStrength.cqi
         */
        response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            if (i > 8) {
                response[i-2] = p.readInt();
                response[i] = -1;
            } else {
                response[i] = p.readInt();
            }
        }

        return response;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch (response) {
            //case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
            case 21004: ret = responseVoid(p); break; // RIL_UNSOL_VOICE_RADIO_TECH_CHANGED
            case 21005: ret = responseVoid(p); break; // RIL_UNSOL_IMS_NETWORK_STATE_CHANGED
            case 21007: ret = responseVoid(p); break; // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);
                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch (response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                int state = p.readInt();
                setRadioStateFromRILInt(state);
                break;
            case 21004:
            case 21005:
            case 21007:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
         }
    }

    private void setRadioStateFromRILInt(int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        IccHandler iccHandler;
        Looper looper;

        switch (stateCode) {
            case RIL_INT_RADIO_OFF:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                if (mIccHandler != null) {
                    mIccThread = null;
                    mIccHandler = null;
                }
                break;
            case RIL_INT_RADIO_UA:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case RIL_INT_RADIO_ON:
                if (mIccHandler == null) {
                    handlerThread = new HandlerThread("IccHandler");
                    mIccThread = handlerThread;
                    mIccThread.start();

                    looper = mIccThread.getLooper();
                    mIccHandler = new IccHandler(this,looper);
                    mIccHandler.run();
                }
                radioState = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }

        setRadioState(radioState);
    }
}
