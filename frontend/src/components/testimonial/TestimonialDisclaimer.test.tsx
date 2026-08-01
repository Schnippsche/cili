import {render, screen} from '@testing-library/react';
import TestimonialDisclaimer from './TestimonialDisclaimer';

test('zeigt den rechtlichen Hinweis zu subjektiven Erfahrungsberichten', () => {
  render(<TestimonialDisclaimer/>);

  expect(screen.getByText(/kein.*wissenschaftlichen Nachweis der Wirksamkeit/i)).toBeInTheDocument();
  expect(screen.getByText(/medizinischem Fachpersonal abgestimmt/i)).toBeInTheDocument();
});
