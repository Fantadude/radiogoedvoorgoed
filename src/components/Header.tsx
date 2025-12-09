import { Radio } from "lucide-react";

const Header = () => {
  return (
    <header className="sticky top-0 z-40 bg-background/80 backdrop-blur-xl border-b border-border safe-top">
      <div className="flex items-center justify-center gap-3 py-4 px-4">
        <div className="w-10 h-10 rounded-xl radio-gradient flex items-center justify-center">
          <Radio className="w-5 h-5 text-primary-foreground" />
        </div>
        <div>
          <h1 className="text-lg font-bold leading-tight">GoedvoorGoed</h1>
          <p className="text-xs text-muted-foreground">Radio & Podcasts</p>
        </div>
      </div>
    </header>
  );
};

export default Header;
