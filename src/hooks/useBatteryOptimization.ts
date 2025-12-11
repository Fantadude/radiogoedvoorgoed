import { useEffect, useState } from "react";
import { Capacitor } from "@capacitor/core";

export const useBatteryOptimization = () => {
  const [isIgnoring, setIsIgnoring] = useState<boolean | null>(null);

  useEffect(() => {
    const checkAndRequestBatteryOptimization = async () => {
      if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== "android") {
        return;
      }

      // Check if we've already prompted this session
      const alreadyPrompted = sessionStorage.getItem("battery_optimization_prompted");
      if (alreadyPrompted) {
        return;
      }

      try {
        const { BatteryOptimization } = await import(
          "@capawesome-team/capacitor-android-battery-optimization"
        );

        // Check if already ignoring battery optimizations
        const result = await BatteryOptimization.isBatteryOptimizationEnabled();
        setIsIgnoring(!result.enabled);

        if (result.enabled) {
          // Request to ignore battery optimizations - this opens system dialog
          await BatteryOptimization.requestIgnoreBatteryOptimization();
        }

        sessionStorage.setItem("battery_optimization_prompted", "true");
      } catch (error) {
        console.error("Failed to check/request battery optimization exemption:", error);
      }
    };

    checkAndRequestBatteryOptimization();
  }, []);

  return { isIgnoring };
};
