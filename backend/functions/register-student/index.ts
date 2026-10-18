import { serve,json } from '../_shared/runtime.ts';
import { registerStudent } from '../../lib/v23.ts';
serve(async(request,{admin})=>json(await registerStudent(admin,await request.json())));
