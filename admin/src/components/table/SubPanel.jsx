import TableWrapper from "./TableWrapper";

export default function SubPanel({ className, module, parentId, admin }) {
  return (
    <TableWrapper
      isSubPanel
      module={module}
      parentId={parentId}
      admin={admin}
      pageSize={10}
      className={"p-0 h-fit"}
      enableSearch={false}
    />
  );
}
