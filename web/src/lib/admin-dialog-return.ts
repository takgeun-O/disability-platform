// Ephemeral UI-only return transaction; never persisted or used for API requests.
export type AdminDialogReturn = { href:string; selector:string; x:number; y:number }
let pending:AdminDialogReturn|null=null
export const getAdminDialogReturn=()=>pending
export const setAdminDialogReturn=(value:AdminDialogReturn|null)=>{pending=value}
