import { useEffect, useRef } from "react";
import { Capacitor } from "@capacitor/core";

/**
 * Hook that detects when audio output device changes (e.g., Bluetooth disconnect)
 * and calls the provided callback to pause audio playback.
 */
export const useAudioOutputChange = (onOutputChange: () => void) => {
  const previousDevicesRef = useRef<string[]>([]);

  useEffect(() => {
    // Only set up listeners if we're on a native platform or browser supports it
    if (!navigator.mediaDevices?.enumerateDevices) {
      console.log("MediaDevices API not supported");
      return;
    }

    const checkForDisconnection = async () => {
      try {
        const devices = await navigator.mediaDevices.enumerateDevices();
        const audioOutputs = devices
          .filter((d) => d.kind === "audiooutput")
          .map((d) => d.deviceId);

        // Check if we had more audio outputs before (device disconnected)
        if (
          previousDevicesRef.current.length > 0 &&
          audioOutputs.length < previousDevicesRef.current.length
        ) {
          console.log("Audio output device disconnected, pausing playback");
          onOutputChange();
        }

        previousDevicesRef.current = audioOutputs;
      } catch (error) {
        console.error("Error checking audio devices:", error);
      }
    };

    // Initial device enumeration
    checkForDisconnection();

    // Listen for device changes
    const handleDeviceChange = () => {
      checkForDisconnection();
    };

    navigator.mediaDevices.addEventListener("devicechange", handleDeviceChange);

    // On Android/Capacitor, also listen for visibility changes as a fallback
    // because devicechange event might not fire reliably for Bluetooth
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        // Small delay to let the system settle after visibility change
        setTimeout(checkForDisconnection, 500);
      }
    };

    if (Capacitor.isNativePlatform()) {
      document.addEventListener("visibilitychange", handleVisibilityChange);
    }

    return () => {
      navigator.mediaDevices.removeEventListener("devicechange", handleDeviceChange);
      if (Capacitor.isNativePlatform()) {
        document.removeEventListener("visibilitychange", handleVisibilityChange);
      }
    };
  }, [onOutputChange]);
};
