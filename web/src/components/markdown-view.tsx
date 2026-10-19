import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

// Styles écrits à la main plutôt qu'un plugin typography : garde le rendu
// sobre et donne un contrôle précis sur les tableaux, qui doivent rester
// lisibles (bordures fines, en-tête légèrement teinté, pas d'ombre).
const components: Components = {
  h1: (props) => <h1 className="mt-0 mb-3 text-base font-semibold" {...props} />,
  h2: (props) => <h2 className="mt-5 mb-2 text-sm font-semibold" {...props} />,
  h3: (props) => <h3 className="mt-4 mb-1.5 text-sm font-semibold" {...props} />,
  p: (props) => <p className="mb-3 leading-relaxed last:mb-0" {...props} />,
  ul: (props) => <ul className="mb-3 list-disc space-y-1 pl-5" {...props} />,
  ol: (props) => <ol className="mb-3 list-decimal space-y-1 pl-5" {...props} />,
  li: (props) => <li className="leading-relaxed" {...props} />,
  strong: (props) => <strong className="font-semibold" {...props} />,
  a: (props) => <a className="text-primary underline underline-offset-2" {...props} />,
  code: (props) => (
    <code className="rounded-sm bg-muted px-1 py-0.5 font-mono text-[0.85em]" {...props} />
  ),
  hr: (props) => <hr className="my-4 border-border" {...props} />,
  table: (props) => (
    <div className="mb-3 overflow-x-auto rounded-md border">
      <table className="w-full border-collapse text-left text-sm" {...props} />
    </div>
  ),
  thead: (props) => <thead className="bg-secondary" {...props} />,
  th: (props) => (
    <th className="border-b border-border px-3 py-1.5 font-medium text-secondary-foreground" {...props} />
  ),
  td: (props) => <td className="border-b border-border px-3 py-1.5 align-top last:border-b-0" {...props} />,
  tr: (props) => <tr className="last:[&>td]:border-b-0" {...props} />,
};

export function MarkdownView({ content }: { content: string }) {
  return (
    <div className="text-sm text-foreground">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
