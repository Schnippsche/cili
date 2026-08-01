import {Alert} from '@mui/material';

export default function TestimonialDisclaimer() {
  return (
      <Alert severity="info" sx={{mb: 3}}>
        Hinweis: Es handelt sich um einen persönlichen Erfahrungsbericht mit subjektiven
        Schilderungen. Die genannten Verbesserungen sind individuelle Erfahrungen und stellen
        keinen wissenschaftlichen Nachweis der Wirksamkeit des Produkts dar. Bei gesundheitlichen
        Beschwerden sollte die Behandlung mit medizinischem Fachpersonal abgestimmt werden.
      </Alert>
  );
}
