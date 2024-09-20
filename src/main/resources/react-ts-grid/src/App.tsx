import React from 'react';
import { Grid, Paper } from '@mui/material';

// Define the interface for each GridItem
interface GridItemProps {
  index: number;
}

// Functional Component for each grid item
const GridItem: React.FC<GridItemProps> = ({ index }) => {
  return (
    <Paper
      elevation={2}
      style={{
        padding: '10px',
        textAlign: 'center',
        fontSize: '14px',
        fontWeight: 'bold',
      }}
    >
      Item {index + 1}
    </Paper>
  );
};

// Main App Component
const App: React.FC = () => {
  // Create an array with 50 items (5 columns x 10 rows)
  const items = Array.from({ length: 50 }, (_, i) => i);

  return (
    <Grid container spacing={2}>
      {items.map((item, index) => (
        <Grid item xs={12} sm={6} md={4} lg={2} key={index}>
          <GridItem index={index} />
        </Grid>
      ))}
    </Grid>
  );
};

export default App;
