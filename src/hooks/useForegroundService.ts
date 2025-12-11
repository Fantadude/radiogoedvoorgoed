import { useCallback } from "react";
import { Capacitor } from "@capacitor/core";

// Dynamic import to avoid errors on web
const getForegroundService = async () => {
  if (Capacitor.isNativePlatform()) {
    const { ForegroundService } = await import("@capawesome-team/capacitor-android-foreground-service");
    return ForegroundService;
  }
  return null;
};

export const useForegroundService = () => {
  const startForegroundService = useCallback(async (title: string, body: string) => {
    try {
      const ForegroundService = await getForegroundService();
      if (!ForegroundService) return;

      // Check and request permissions
      const permStatus = await ForegroundService.checkPermissions();
      if (permStatus.display !== "granted") {
        const result = await ForegroundService.requestPermissions();
        if (result.display !== "granted") {
          console.warn("Foreground service permission denied");
          return;
        }
      }

      // Start the foreground service with notification
      await ForegroundService.startForegroundService({
        id: 1,
        title: title,
        body: body,
        smallIcon: "ic_stat_radio",
        silent: true,
      });
      
      console.log("Foreground service started");
    } catch (error) {
      console.error("Failed to start foreground service:", error);
    }
  }, []);

  const updateForegroundService = useCallback(async (title: string, body: string) => {
    try {
      const ForegroundService = await getForegroundService();
      if (!ForegroundService) return;

      await ForegroundService.updateForegroundService({
        id: 1,
        title: title,
        body: body,
        smallIcon: "ic_stat_radio",
        silent: true,
      });
    } catch (error) {
      console.error("Failed to update foreground service:", error);
    }
  }, []);

  const stopForegroundService = useCallback(async () => {
    try {
      const ForegroundService = await getForegroundService();
      if (!ForegroundService) return;

      await ForegroundService.stopForegroundService();
      console.log("Foreground service stopped");
    } catch (error) {
      console.error("Failed to stop foreground service:", error);
    }
  }, []);

  return {
    startForegroundService,
    updateForegroundService,
    stopForegroundService,
  };
};
