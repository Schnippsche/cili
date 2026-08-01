import {Alert} from '@mui/material';

export default function TestimonialDisclaimer() {
  return (
      <Alert severity="info" sx={{mb: 3}}>
        Hinweis: Die nachfolgenden Berichte geben ausschließlich die persönlichen Erfahrungen der
        jeweiligen Verfasser wieder. Es handelt sich um individuelle Schilderungen, die subjektive
        Eindrücke wiedergeben. Die Inhalte stellen keine wissenschaftlich belegten Aussagen dar und
        sind nicht als Nachweis einer bestimmten Wirkung oder als Heilversprechen zu verstehen. Aus
        den geschilderten Erfahrungen können keine Rückschlüsse auf vergleichbare Ergebnisse bei
        anderen Personen gezogen werden.
      </Alert>
  );
}
